// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.fab.AppFAB
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
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
                    TrainingsEmptyState(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        val isSelecting = state.isSelecting
        AppFAB(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppDimension.screenEdge)
                .testTag("AllTrainingsFab"),
            icon = if (isSelecting) Icons.Filled.Delete else Icons.Filled.Add,
            contentDescription = stringResource(
                if (isSelecting) R.string.feature_all_trainings_bulk_archive
                else R.string.feature_all_trainings_fab_create,
            ),
            containerColor = if (isSelecting) {
                AppUi.colors.status.error
            } else {
                AppUi.colors.accent
            },
            onClick = {
                if (isSelecting) consume(Action.Click.OnBulkDelete)
                else consume(Action.Click.OnFabClick)
            },
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
        contentPadding = PaddingValues(
            start = AppDimension.screenEdge,
            end = AppDimension.screenEdge,
            top = AppDimension.Space.sm,
            bottom = AppDimension.heightLg + AppDimension.screenEdge,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        items(
            count = typedItems.itemCount,
            key = { index -> typedItems.peek(index)?.uuid ?: "training_$index" },
        ) { index ->
            typedItems[index]?.let { item ->
                TrainingRow(
                    item = item,
                    isSelected = selectedSet?.contains(item.uuid) == true,
                    onClick = { consume(Action.Click.OnTrainingClick(item.uuid)) },
                    onLongPress = { consume(Action.Click.OnTrainingLongPress(item.uuid)) },
                )
            }
        }
    }
}

private fun LazyPagingItems<*>.isEmptyAndIdle(): Boolean =
    itemCount == 0 &&
        loadState.refresh is LoadState.NotLoading &&
        loadState.append is LoadState.NotLoading &&
        loadState.prepend is LoadState.NotLoading
