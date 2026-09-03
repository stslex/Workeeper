// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

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
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppBlockedArchiveDialog
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
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ColdOpenError
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ColdOpenLoading
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.FilteredEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ListSurface
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.all_exercises.ui.components.SelectionEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TopBarMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.crossfades
import io.github.stslex.workeeper.feature.all_exercises.ui.components.listSurface
import io.github.stslex.workeeper.feature.all_exercises.ui.components.pagingTailKind
import io.github.stslex.workeeper.feature.all_exercises.ui.components.topBarMode

@Composable
internal fun AllExercisesScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.pagingUiState.collectAsItems()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("AllExercisesScreen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(state = state, consume = consume)
            // The band stays visible during selection (spec C4) so the list does not jump.
            if (state.availableTags.isNotEmpty()) {
                TagFilterRow(
                    tags = state.availableTags,
                    activeTagFilter = state.activeTagFilter,
                    onToggle = { uuid -> consume(Action.Click.OnTagFilterToggle(uuid)) },
                )
            }
            // GUARD: keep `fillMaxSize` — the empty region measures with `matchParentSize`, so
            // without it the region collapses to zero width whenever the list is absent.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                // GUARD: one deferred reading drives both bodies; an independent list check
                // would draw rows under the spinner for the rest of the minimum hold.
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
                    ExercisesList(state = state, items = items, consume = consume)
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
        // Selection morph is shape and glyph only; the fill stays `--max` (spec §26).
        AppFAB(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppDimension.screenEdge)
                .testTag("AllExercisesFab"),
            icon = if (isSelecting) AppIcons.FabArchive else AppIcons.FabPlus,
            contentDescription = stringResource(
                if (isSelecting) R.string.feature_all_exercises_bulk_archive
                else R.string.feature_all_exercises_fab_create,
            ),
            cornerRadius = if (isSelecting) FAB_MORPHED_RADIUS else AppDimension.Radius.medium,
            onClick = {
                if (isSelecting) consume(Action.Click.OnBulkDelete)
                else consume(Action.Click.OnFabClick)
            },
        )
    }

    state.pendingPermanentDelete?.let { pending ->
        AppConfirmDialog(
            title = stringResource(
                R.string.feature_all_exercises_permanent_delete_title,
                pending.name,
            ),
            body = stringResource(R.string.feature_all_exercises_permanent_delete_body),
            impactSummary = stringResource(R.string.feature_all_exercises_permanent_delete_impact),
            confirmLabel = stringResource(R.string.feature_all_exercises_permanent_delete_confirm),
            onConfirm = { consume(Action.Click.OnConfirmPermanentDelete) },
            onDismiss = { consume(Action.Click.OnCancelPermanentDelete) },
        )
    }

    state.pendingBulkDelete?.let { pending ->
        AppConfirmDialog(
            title = stringResource(R.string.feature_all_exercises_bulk_archive_confirm_title),
            body = pluralStringResource(
                R.plurals.feature_all_exercises_bulk_archive_confirm_body,
                pending.count,
                pending.count,
            ),
            impactSummary = stringResource(R.string.feature_all_exercises_bulk_archive_impact),
            confirmLabel = stringResource(R.string.feature_all_exercises_bulk_archive),
            onConfirm = { consume(Action.Click.OnBulkDeleteConfirm) },
            onDismiss = { consume(Action.Click.OnBulkDeleteDismiss) },
        )
    }

    state.blockedArchiveDialog?.let { dialog ->
        AppBlockedArchiveDialog(
            title = stringResource(R.string.feature_all_exercises_blocked_archive_title),
            items = dialog.items,
            archivedSummary = dialog.archivedSummary,
            nextStep = stringResource(R.string.feature_all_exercises_blocked_archive_next_step),
            confirmLabel = stringResource(R.string.feature_all_exercises_blocked_archive_confirm),
            onDismiss = { consume(Action.Click.OnBlockedArchiveDismiss) },
        )
    }
}

/**
 * Top bar for the list; crossfades whole between resting and selection (spec §26), keyed on
 * [topBarMode]. [lastSelectionCount] caches the count the outgoing bar needs during the exit.
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
                modifier = Modifier.testTag("AllExercisesTopBar"),
                title = stringResource(R.string.feature_all_exercises_title),
            )
        }
    }
}

/** The replacement bar in selection mode: close, count, archive (spec §26). */
@Composable
private fun SelectionTopBar(
    count: Int,
    consume: (Action) -> Unit,
) {
    AppTopAppBar(
        modifier = Modifier.testTag("AllExercisesSelectionTopBar"),
        title = pluralStringResource(
            R.plurals.feature_all_exercises_selected_count,
            count,
            count,
        ),
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("AllExercisesSelectionTopBarClose"),
                onClick = { consume(Action.Click.OnSelectionExit) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.feature_all_exercises_selection_close,
                    ),
                )
            }
        },
        actions = {
            IconButton(
                modifier = Modifier.testTag("AllExercisesSelectionTopBarArchive"),
                onClick = { consume(Action.Click.OnBulkDelete) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = AppIcons.Archive,
                    contentDescription = stringResource(
                        R.string.feature_all_exercises_bulk_archive,
                    ),
                )
            }
        },
    )
}

@Composable
private fun ExercisesList(
    state: State,
    items: LazyPagingItems<ExerciseUiModel>,
    consume: (Action) -> Unit,
) {
    val selectedSet = (state.selectionMode as? SelectionMode.On)?.selectedUuids
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("AllExercisesList"),
        // Full-bleed rows own their gutter and rule; the bottom is FAB clearance and nothing else.
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_CLEARANCE),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { item ->
                ExerciseRow(
                    // Continuity motion for add / remove / reorder; one shared spec across
                    // all three channels (spec §26).
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = item,
                    isSelected = selectedSet?.contains(item.uuid) == true,
                    isSelecting = state.isSelecting,
                    showDivider = index < items.itemCount - 1,
                    onClick = { consume(Action.Click.OnExerciseClick(item.uuid)) },
                    onLongPress = { consume(Action.Click.OnExerciseLongPress(item.uuid)) },
                )
            }
        }
        pagingTail(items = items, onRetry = { items.retry() })
    }
}

/**
 * The empty region: one verdict per state from [listSurface], crossfaded between the verdicts
 * [ListSurface.crossfades] admits. The inner `Box` gives each branch its own align scope.
 */
@Composable
private fun BoxScope.EmptyRegion(
    deferredSurface: ListSurface?,
    state: State,
    items: LazyPagingItems<ExerciseUiModel>,
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

                ListSurface.FIRST_RUN -> ExercisesEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onCreate = { consume(Action.Click.OnEmptyCreate) },
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
 * Paging tail: a spinner while appending, a reason plus retry on failure, nothing when exhausted
 * (spec §26). The decision itself lives in [pagingTailKind]; no golden can see it.
 */
private fun LazyListScope.pagingTail(
    items: LazyPagingItems<ExerciseUiModel>,
    onRetry: () -> Unit,
) {
    when (pagingTailKind(items.loadState.append)) {
        PagingTailKind.LOADING -> item(key = "paging_loading") { PagingLoadingFooter() }
        PagingTailKind.ERROR -> item(key = "paging_error") { PagingErrorFooter(onRetry = onRetry) }
        PagingTailKind.NONE -> Unit
    }
}

/**
 * FAB clearance under the list: `16 + 56 + 16` = 88 (spec §26). No golden can see
 * `contentPadding.bottom`; `AllExercisesClearanceTest` is the gate.
 */
internal val LIST_BOTTOM_CLEARANCE: Dp =
    AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge

/** 28dp on a 56dp button — the circle the squircle opens into. */
private val FAB_MORPHED_RADIUS = 28.dp
