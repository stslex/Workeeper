// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppBlockedArchiveDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.fab.AppFAB
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.pagingTailKind

@Composable
internal fun AllExercisesScreen(
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
            .testTag("AllExercisesScreen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(state = state, consume = consume)
            // The band stays visible during selection (spec C4) so the list does not vertically
            // jump on entering the mode.
            if (state.availableTags.isNotEmpty()) {
                TagFilterRow(
                    tags = state.availableTags,
                    activeTagFilter = state.activeTagFilter,
                    onToggle = { uuid -> consume(Action.Click.OnTagFilterToggle(uuid)) },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ExercisesList(state = state, items = items, consume = consume)
                if (items.isEmptyAndIdle() && !state.isSelecting) {
                    ExercisesEmptyState(
                        modifier = Modifier.align(Alignment.Center),
                        onCreate = { consume(Action.Click.OnEmptyCreate) },
                    )
                }
            }
        }
        val isSelecting = state.isSelecting
        // §26 "FAB in selection mode": the morph is SHAPE AND GLYPH ONLY. The fill stays `--max`
        // and the content `--base` throughout — the action is archive, archive is reversible, and
        // `--rust` marks destruction only (§1), so the old `status.error` fill was promising
        // irreversibility for a reversible act and `Icons.Filled.Delete` was following the fill.
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

@Composable
private fun ScreenTopBar(
    state: State,
    consume: (Action) -> Unit,
) {
    val mode = state.selectionMode
    val isSelecting = mode is SelectionMode.On
    val title = if (mode is SelectionMode.On) {
        pluralStringResource(
            R.plurals.feature_all_exercises_selected_count,
            mode.selectedUuids.size,
            mode.selectedUuids.size,
        )
    } else {
        stringResource(R.string.feature_all_exercises_title)
    }
    AppTopAppBar(
        modifier = Modifier.testTag(
            if (isSelecting) "AllExercisesSelectionTopBar" else "AllExercisesTopBar",
        ),
        title = title,
        navigationIcon = if (isSelecting) {
            {
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
            }
        } else {
            {}
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
        // Full-bleed: the drawn row owns its own gutter and its own rule, so the list adds no
        // horizontal padding and no inter-item spacing. The bottom is FAB clearance and nothing
        // else — §26 "Add action": `16 + 56 + 16` = 88. The navigation bar's inset is the host's,
        // globally, together with the system inset the mockup cannot draw. Without the 88 the tail
        // sits under the button permanently, and on a paged list the tail is live.
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_CLEARANCE),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { item ->
                ExerciseRow(
                    item = item,
                    isSelected = selectedSet?.contains(item.uuid) == true,
                    isSelecting = state.isSelecting,
                    // The drawing removes the last row's rule (`.frame .row:last-of-type`), so the
                    // list does not end on a hairline into empty space.
                    showDivider = index < items.itemCount - 1,
                    onClick = { consume(Action.Click.OnExerciseClick(item.uuid)) },
                    onLongPress = { consume(Action.Click.OnExerciseLongPress(item.uuid)) },
                )
            }
        }
        pagingTail(items = items, onRetry = { items.retry() })
    }
}

private fun LazyPagingItems<*>.isEmptyAndIdle(): Boolean =
    itemCount == 0 &&
        loadState.refresh is LoadState.NotLoading &&
        loadState.append is LoadState.NotLoading &&
        loadState.prepend is LoadState.NotLoading

/**
 * §26 "Paging tails": three states, two drawings.
 *
 * Loading is a footer spinner. **Exhausted is no footer at all** — "end of list" states only what is
 * already visible, so the absence is the drawing. Error is the reason plus a retry, because a
 * silently truncated list is indistinguishable from a finished one.
 *
 * None of this existed here: `loadState.append` was never read, so a failed page was a list that
 * quietly stopped.
 *
 * The **decision** lives in `pagingTailKind` rather than here, because no golden can see it: with
 * the branch inlined, deleting the error case left all 30 goldens byte-identical. This function is
 * now only the dispatch.
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
 * §26 "Add action": `16 + 56 + 16` = **88**. FAB clearance and nothing else — the navigation bar's
 * inset is the host's, globally, together with the system inset the mockup cannot draw.
 *
 * Named rather than inlined because **no golden can see it.** `contentPadding.bottom` moves no pixel
 * in an unscrolled frame, and Paparazzi renders one frame of an unscrolled list, so the visual gate
 * is blind to this value however many goldens the suite grows to. `AllExercisesClearanceTest` is the
 * gate instead. §27 carries the measurement that established it.
 */
internal val LIST_BOTTOM_CLEARANCE: Dp =
    AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge

/** 28dp on a 56dp button — the circle the squircle opens into. */
private val FAB_MORPHED_RADIUS = 28.dp
