// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.BulkActionBar
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.SelectionTopBar
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow

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
            if (state.availableTags.isNotEmpty() && !state.isSelecting) {
                TagFilterRow(
                    tags = state.availableTags,
                    activeTagFilter = state.activeTagFilter,
                    onToggle = { uuid -> consume(Action.Click.OnTagFilterToggle(uuid)) },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("AllExercisesList"),
                    contentPadding = PaddingValues(
                        horizontal = AppDimension.screenEdge,
                        vertical = AppDimension.Space.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
                ) {
                    items(
                        count = items.itemCount,
                        key = { index -> items.peek(index)?.uuid ?: "exercise_$index" },
                    ) { index ->
                        items[index]?.let { item ->
                            val selected = (state.selectionMode as? SelectionMode.On)
                                ?.selectedUuids
                                ?.contains(item.uuid) == true
                            ExerciseRow(
                                item = item,
                                isSelectionMode = state.isSelecting,
                                isSelected = selected,
                                onClick = { consume(Action.Click.OnExerciseClick(item.uuid)) },
                                onLongPress = {
                                    consume(Action.Click.OnExerciseLongPress(item.uuid))
                                },
                                onArchive = {
                                    consume(Action.Click.OnArchiveSwipe(item.uuid, item.name))
                                },
                            )
                        }
                    }
                }
                if (items.isEmptyAndIdle() && !state.isSelecting) {
                    ExercisesEmptyState(modifier = Modifier.align(Alignment.Center))
                }
            }
            if (state.isSelecting) {
                val selectionMode = state.selectionMode as SelectionMode.On
                BulkActionBar(
                    canDelete = selectionMode.canDeleteAll,
                    onArchive = { consume(Action.Click.OnBulkArchive) },
                    onDelete = { consume(Action.Click.OnBulkDelete) },
                )
            }
        }
        if (!state.isSelecting) {
            AppFAB(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppDimension.screenEdge)
                    .testTag("AllExercisesFab"),
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.feature_all_exercises_fab_create),
                onClick = { consume(Action.Click.OnFabClick) },
            )
        }
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
            title = stringResource(R.string.feature_all_exercises_bulk_delete_confirm_title),
            body = pluralStringResource(
                R.plurals.feature_all_exercises_bulk_delete_confirm_body,
                pending.count,
                pending.count,
            ),
            impactSummary = stringResource(R.string.feature_all_exercises_bulk_delete_impact),
            confirmLabel = stringResource(R.string.feature_all_exercises_bulk_delete),
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
    if (mode is SelectionMode.On) {
        SelectionTopBar(
            selectedCount = mode.selectedUuids.size,
            onClose = { consume(Action.Click.OnSelectionExit) },
        )
    } else {
        AppTopAppBar(
            title = stringResource(R.string.feature_all_exercises_title),
        )
    }
}

private fun LazyPagingItems<*>.isEmptyAndIdle(): Boolean =
    itemCount == 0 &&
        loadState.refresh is LoadState.NotLoading &&
        loadState.append is LoadState.NotLoading &&
        loadState.prepend is LoadState.NotLoading
