// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

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
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.fab.AppFAB
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ColdOpenLoading
import io.github.stslex.workeeper.feature.all_trainings.ui.components.FilteredEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ListSurface
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.all_trainings.ui.components.SelectionEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingsEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.listSurface
import io.github.stslex.workeeper.feature.all_trainings.ui.components.pagingTailKind

@Composable
internal fun AllTrainingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(state.pagingUiState) {
        state.pagingUiState()
    }.collectAsLazyPagingItems()

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
            Box(modifier = Modifier.weight(1f)) {
                TrainingsList(
                    state = state,
                    items = items,
                    consume = consume,
                )
                EmptyRegion(state = state, items = items, consume = consume)
            }
        }
        val isSelecting = state.isSelecting
        // §26 "FAB in selection mode": the morph is SHAPE AND GLYPH ONLY. The fill stays `--max`
        // and the content `--base` throughout — the action is archive, archive is reversible, and
        // `--rust` marks destruction only (§1), so the old rust fill was promising irreversibility
        // for a reversible act and the trash glyph was following the fill.
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

@Composable
private fun ScreenTopBar(
    state: State,
    consume: (Action) -> Unit,
) {
    val mode = state.selectionMode
    val isSelecting = mode is SelectionMode.On
    val title = if (mode is SelectionMode.On) {
        pluralStringResource(
            R.plurals.feature_all_trainings_selected_count,
            mode.selectedUuids.size,
            mode.selectedUuids.size,
        )
    } else {
        stringResource(R.string.feature_all_trainings_title)
    }
    AppTopAppBar(
        modifier = Modifier.testTag(
            if (isSelecting) "AllTrainingsSelectionTopBar" else "AllTrainingsTopBar",
        ),
        title = title,
        navigationIcon = if (isSelecting) {
            {
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
            }
        } else {
            null
        },
        // §26 "Selection mode": the top bar is replaced whole — count PLUS actions. The archive
        // action was drawn from the start and never built; the FAB is not the only affordance the
        // drawing gives this mode.
        actions = if (isSelecting) {
            {
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
            }
        } else {
            {}
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
        // Full-bleed: the drawn row owns its own gutter and its own rule, so the list adds no
        // horizontal padding and no inter-item spacing. The bottom is FAB clearance and nothing
        // else — §26 "Add action": `16 + 56 + 16` = 88. The navigation bar's inset is the host's,
        // globally, together with the system inset the mockup cannot draw. Without the 88 the tail
        // sits under the button permanently, and on a paged list the tail is live.
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_CLEARANCE),
    ) {
        items(
            count = typedItems.itemCount,
            key = { index -> typedItems.peek(index)?.uuid ?: "training_$index" },
        ) { index ->
            typedItems[index]?.let { item ->
                TrainingRow(
                    item = item,
                    isSelected = selectedSet?.contains(item.uuid) == true,
                    isSelecting = state.isSelecting,
                    // The drawing removes the last row's rule (`.frame .row:last-of-type`), so the
                    // list does not end on a hairline into empty space.
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
 * The empty region — one selector, five verdicts, three of them new.
 *
 * This replaces `isEmptyAndIdle() && !isSelecting`, a single branch in which four different states
 * were living. See [listSurface] for the ordering and the reasoning; the states themselves are
 * drawn in `#s-empty` and ruled by §26 "List states reached by an action".
 *
 * [ListSurface.REFRESH_ERROR] renders **nothing**, and that is deliberate rather than an omission:
 * a failed *first* page is B22's fourth region and the contract does not draw it. Inventing a
 * treatment for it here is exactly the derivation §0.1 exists to prevent, and mapping it onto the
 * loading spinner or the first-run empty would be a lie about what happened.
 */
@Composable
private fun BoxScope.EmptyRegion(
    state: State,
    items: LazyPagingItems<TrainingListItemUi>,
    consume: (Action) -> Unit,
) {
    val filterActive = state.activeTagFilter.isNotEmpty()
    val clearFilter = { consume(Action.Click.OnClearTagFilter) }
    when (
        listSurface(
            itemCount = items.itemCount,
            loadState = items.loadState,
            filterActive = filterActive,
            selecting = state.isSelecting,
        )
    ) {
        ListSurface.CONTENT, ListSurface.REFRESH_ERROR -> Unit

        ListSurface.LOADING -> ColdOpenLoading(modifier = Modifier.align(Alignment.TopCenter))

        ListSurface.FIRST_RUN -> TrainingsEmptyState(
            modifier = Modifier.align(Alignment.Center),
            onCreate = { consume(Action.Click.OnEmptyCreate) },
            onStartBlank = { consume(Action.Click.OnEmptyStartBlank) },
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

/**
 * §26 "Paging tails": three states, two drawings.
 *
 * Loading is a footer spinner. **Exhausted is no footer at all** — "end of list" states only what
 * is already visible, so the absence is the drawing. Error is the reason plus **Повторить**,
 * because a silently truncated list is indistinguishable from a finished one.
 *
 * None of this existed before: `loadState.append` was never read, so a failed page was a list that
 * quietly stopped.
 *
 * The **decision** lives in `pagingTailKind` rather than here, because no golden can see it — the
 * hole was measured on the sibling screen, where deleting the error case moved no pixel anywhere.
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
 * §26 "Add action": `16 + 56 + 16` = **88**. FAB clearance and nothing else — the navigation bar's
 * inset is the host's, globally, together with the system inset the mockup cannot draw.
 *
 * Named rather than inlined because **no golden can see it.** `contentPadding.bottom` moves no pixel
 * in an unscrolled frame, and Paparazzi renders one frame of an unscrolled list, so the visual gate
 * is blind to this value however many goldens the suite grows to. `AllTrainingsClearanceTest` is the
 * gate instead. §27 carries the measurement that established it.
 */
internal val LIST_BOTTOM_CLEARANCE: Dp =
    AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge

/** 28dp on a 56dp button — the circle the squircle opens into. */
private val FAB_MORPHED_RADIUS = 28.dp
