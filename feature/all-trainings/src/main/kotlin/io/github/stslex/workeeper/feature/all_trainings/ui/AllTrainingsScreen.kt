// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.collectAsItems
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.fab.AppFAB
import io.github.stslex.workeeper.core.ui.kit.components.paging.ListBody
import io.github.stslex.workeeper.core.ui.kit.components.paging.listBody
import io.github.stslex.workeeper.core.ui.kit.components.paging.rememberDeferredSurface
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec
import io.github.stslex.workeeper.core.ui.kit.theme.continuityPositionalSpec
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ColdOpenError
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ColdOpenLoading
import io.github.stslex.workeeper.feature.all_trainings.ui.components.FilteredEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ListSurface
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.all_trainings.ui.components.SelectionEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TopBarMode
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingsEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.crossfades
import io.github.stslex.workeeper.feature.all_trainings.ui.components.listSurface
import io.github.stslex.workeeper.feature.all_trainings.ui.components.pagingTailKind
import io.github.stslex.workeeper.feature.all_trainings.ui.components.topBarMode

@Composable
internal fun AllTrainingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.pagingUiState.collectAsItems()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("AllTrainingsScreen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(state = state, consume = consume)
            // Tags row stays visible during selection (spec C4).
            if (state.availableTags.isNotEmpty()) {
                TagFilterRow(
                    tags = state.availableTags,
                    activeTagFilter = state.activeTagFilter,
                    onToggle = { uuid -> consume(Action.Click.OnTagFilterToggle(uuid)) },
                )
            }
            // GUARD: keep `fillMaxSize` — EmptyRegion measures with `matchParentSize` and would
            // collapse to zero width in the states where the conditional list child is absent.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                // GUARD: one deferred verdict drives BOTH bodies — a list composed from the raw
                // verdict would draw rows under the spinner for the rest of the minimum hold.
                val surface = rememberDeferredSurface(
                    surface = listSurface(
                        itemCount = items.itemCount,
                        loadState = items.loadState,
                        filterActive = state.activeTagFilter.isNotEmpty(),
                        selecting = state.isSelecting,
                    ),
                    loadingSurface = ListSurface.LOADING,
                )
                if (listBody(surface, ListSurface.CONTENT) == ListBody.ROWS) {
                    TrainingsList(
                        state = state,
                        items = items,
                        consume = consume,
                    )
                }
                EmptyRegion(
                    deferredSurface = surface,
                    state = state,
                    items = items,
                    consume = consume,
                )
            }
        }
        val isSelecting = state.isSelecting
        // §26 "FAB in selection mode": the morph is shape and glyph only; the fill stays `--max`
        // because archive is reversible and `--rust` marks destruction.
        AppFAB(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppDimension.screenEdge)
                .testTag("AllTrainingsFab"),
            icon = if (isSelecting) AppIcons.FabArchive else AppIcons.FabPlus,
            contentDescription = stringResource(
                if (isSelecting) R.string.feature_all_trainings_bulk_archive
                else R.string.feature_all_trainings_fab_create,
            ),
            cornerRadius = if (isSelecting) FAB_MORPHED_RADIUS else AppDimension.Radius.medium,
            onClick = { consume(Action.Click.OnFabClick) },
        )
    }

    state.pendingBulkDelete?.let { pending ->
        AppConfirmDialog(
            title = stringResource(R.string.feature_all_trainings_bulk_archive_confirm_title),
            body = pluralStringResource(
                R.plurals.feature_all_trainings_bulk_archive_confirm_body,
                pending.count,
                pending.count,
            ),
            impactSummary = stringResource(R.string.feature_all_trainings_bulk_archive_impact),
            confirmLabel = stringResource(R.string.feature_all_trainings_bulk_archive),
            onConfirm = { consume(Action.Click.OnBulkDeleteConfirm) },
            onDismiss = { consume(Action.Click.OnBulkDeleteDismiss) },
        )
    }
}

/**
 * The list top bar: replaced whole and crossfaded on [topBarMode], never the title, so a changing
 * count never animates. The cached count feeds the exit fade, when the mode is already `Off`.
 */
@Composable
private fun ScreenTopBar(
    state: State,
    consume: (Action) -> Unit,
) {
    val mode = state.selectionMode
    val spec = continuityAlphaSpec<Float>()
    val lastSelectionCount = remember { intArrayOf(0) }
    if (mode is SelectionMode.On) lastSelectionCount[0] = mode.selectedUuids.size

    AnimatedContent(
        targetState = topBarMode(mode),
        transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) using null },
        label = "list-top-bar",
    ) { barMode ->
        when (barMode) {
            TopBarMode.SELECTION -> SelectionTopBar(
                count = lastSelectionCount[0],
                consume = consume,
            )

            TopBarMode.RESTING -> AppTopAppBar(
                modifier = Modifier.testTag("AllTrainingsTopBar"),
                title = stringResource(R.string.feature_all_trainings_title),
            )
        }
    }
}

/** The selection bar: close, count, archive — §26 "Selection mode" wants count plus actions. */
@Composable
private fun SelectionTopBar(
    count: Int,
    consume: (Action) -> Unit,
) {
    AppTopAppBar(
        modifier = Modifier.testTag("AllTrainingsSelectionTopBar"),
        title = pluralStringResource(
            R.plurals.feature_all_trainings_selected_count,
            count,
            count,
        ),
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("AllTrainingsSelectionTopBarClose"),
                onClick = { consume(Action.Click.OnSelectionExit) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.feature_all_trainings_selection_close,
                    ),
                )
            }
        },
        actions = {
            IconButton(
                modifier = Modifier.testTag("AllTrainingsSelectionTopBarArchive"),
                onClick = { consume(Action.Click.OnFabClick) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = AppIcons.Archive,
                    contentDescription = stringResource(
                        R.string.feature_all_trainings_bulk_archive,
                    ),
                )
            }
        },
    )
}

@Composable
private fun TrainingsList(
    state: State,
    items: LazyPagingItems<*>,
    consume: (Action) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val typedItems = items
        as LazyPagingItems<io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi>
    val selectedSet = (state.selectionMode as? SelectionMode.On)?.selectedUuids
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("AllTrainingsList"),
        // Full-bleed: the row owns its gutter and rule. Bottom padding is FAB clearance only
        // (§26 "Add action"); the navigation-bar inset is the host's.
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_CLEARANCE),
    ) {
        items(
            count = typedItems.itemCount,
            key = { index -> typedItems.peek(index)?.uuid ?: "training_$index" },
        ) { index ->
            typedItems[index]?.let { item ->
                TrainingRow(
                    // §26 continuity motion: rows are added, removed and reordered by archive and
                    // restore, so the settle costs one modifier. One shared spec on all channels.
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = item,
                    isSelected = selectedSet?.contains(item.uuid) == true,
                    isSelecting = state.isSelecting,
                    // The drawing drops the last row's rule: no hairline into empty space.
                    showDivider = index < typedItems.itemCount - 1,
                    onClick = { consume(Action.Click.OnTrainingClick(item.uuid)) },
                    onLongPress = { consume(Action.Click.OnTrainingLongPress(item.uuid)) },
                )
            }
        }
        pagingTail(items = typedItems, onRetry = { typedItems.retry() })
    }
}

/**
 * The empty region: the [listSurface] verdict picks the block, crossfaded for the verdicts
 * [ListSurface.crossfades] admits. The inner `Box` gives each branch a [BoxScope] for `align`.
 */
@Composable
private fun BoxScope.EmptyRegion(
    deferredSurface: ListSurface?,
    state: State,
    items: LazyPagingItems<TrainingListItemUi>,
    consume: (Action) -> Unit,
) {
    val filterActive = state.activeTagFilter.isNotEmpty()
    val clearFilter = { consume(Action.Click.OnClearTagFilter) }
    val spec = continuityAlphaSpec<Float>()
    val surface = deferredSurface ?: return
    if (surface.crossfades.not()) {
        if (surface == ListSurface.LOADING) {
            ColdOpenLoading(modifier = Modifier.align(Alignment.TopCenter))
        }
        return
    }

    AnimatedContent(
        modifier = Modifier.matchParentSize(),
        targetState = surface,
        transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) using null },
        label = "list-empty-region",
    ) { block ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (block) {
                // Unreachable: `crossfades` returned above. Named so a new verdict breaks here.
                ListSurface.CONTENT, ListSurface.LOADING -> Unit

                ListSurface.REFRESH_ERROR -> ColdOpenError(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onRetry = { items.retry() },
                )

                ListSurface.FIRST_RUN -> TrainingsEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onCreate = { consume(Action.Click.OnEmptyCreate) },
                    // Withdrawn while a workout is running, so the empty state cannot start a
                    // second session. See `State.showStartBlank`.
                    onStartBlank = { consume(Action.Click.OnEmptyStartBlank) }
                        .takeIf { state.showStartBlank },
                )

                ListSurface.FILTERED_EMPTY -> FilteredEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onClearFilter = clearFilter,
                )

                ListSurface.SELECTION_EMPTY -> SelectionEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onClearFilter = clearFilter.takeIf { filterActive },
                )
            }
        }
    }
}

/**
 * §26 "Paging tails": spinner while loading, the reason plus retry on error, nothing when
 * exhausted. The decision itself lives in [pagingTailKind], where a unit test can see it.
 */
private fun LazyListScope.pagingTail(
    items: LazyPagingItems<TrainingListItemUi>,
    onRetry: () -> Unit,
) {
    when (pagingTailKind(items.loadState.append)) {
        PagingTailKind.LOADING -> item(key = "paging_loading") { PagingLoadingFooter() }
        PagingTailKind.ERROR -> item(key = "paging_error") { PagingErrorFooter(onRetry = onRetry) }
        PagingTailKind.NONE -> Unit
    }
}

/**
 * FAB clearance for the list's bottom padding: `16 + 56 + 16` = 88 (§26 "Add action"). Named
 * because no golden can see `contentPadding.bottom`; `AllTrainingsClearanceTest` is the gate.
 */
internal val LIST_BOTTOM_CLEARANCE: Dp =
    AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge

/** 28dp on a 56dp button — the circle the squircle opens into. */
private val FAB_MORPHED_RADIUS = 28.dp
