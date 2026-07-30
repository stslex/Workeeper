// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

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
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
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
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingsEmptyState

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
                if (items.isEmptyAndIdle() && !state.isSelecting) {
                    TrainingsEmptyState(
                        modifier = Modifier.align(Alignment.Center),
                        onCreate = { consume(Action.Click.OnEmptyCreate) },
                        onStartBlank = { consume(Action.Click.OnEmptyStartBlank) },
                    )
                }
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
        contentPadding = PaddingValues(
            bottom = AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge,
        ),
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

private fun LazyPagingItems<*>.isEmptyAndIdle(): Boolean =
    itemCount == 0 &&
        loadState.refresh is LoadState.NotLoading &&
        loadState.append is LoadState.NotLoading &&
        loadState.prepend is LoadState.NotLoading

/**
 * §26 "Paging tails": three states, two drawings.
 *
 * Loading is a footer spinner. **Exhausted is no footer at all** — "end of list" states only what
 * is already visible, so the absence is the drawing. Error is the reason plus **Повторить**,
 * because a silently truncated list is indistinguishable from a finished one.
 *
 * None of this existed before: `loadState.append` was never read, so a failed page was a list that
 * quietly stopped.
 */
private fun LazyListScope.pagingTail(
    items: LazyPagingItems<TrainingListItemUi>,
    onRetry: () -> Unit,
) {
    when (items.loadState.append) {
        is LoadState.Loading -> item(key = "paging_loading") { PagingLoadingFooter() }
        is LoadState.Error -> item(key = "paging_error") { PagingErrorFooter(onRetry = onRetry) }
        // Exhausted, and everything else: no footer. Drawn as an absence, built as one.
        else -> Unit
    }
}

/** 28dp on a 56dp button — the circle the squircle opens into. */
private val FAB_MORPHED_RADIUS = 28.dp
