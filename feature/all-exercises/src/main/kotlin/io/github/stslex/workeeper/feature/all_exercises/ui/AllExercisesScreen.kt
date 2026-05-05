// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

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
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
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
            // TagFilterRow stays visible during selection mode (spec C4) so the list
            // does not vertically jump when entering selection.
            if (state.availableTags.isNotEmpty()) {
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
                        start = AppDimension.screenEdge,
                        end = AppDimension.screenEdge,
                        top = AppDimension.Space.sm,
                        // FAB clearance — heightLg covers the 56dp FAB diameter, screenEdge
                        // is the FAB's bottom anchor padding.
                        bottom = AppDimension.heightLg + AppDimension.screenEdge,
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
                                isSelected = selected,
                                onClick = { consume(Action.Click.OnExerciseClick(item.uuid)) },
                                onLongPress = {
                                    consume(Action.Click.OnExerciseLongPress(item.uuid))
                                },
                            )
                        }
                    }
                }
                if (items.isEmptyAndIdle() && !state.isSelecting) {
                    ExercisesEmptyState(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        // FAB is always visible. Its icon morphs `+` ↔ trash to expose bulk delete in
        // selection mode (spec C5).
        val isSelecting = state.isSelecting
        AppFAB(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppDimension.screenEdge)
                .testTag("AllExercisesFab"),
            icon = if (isSelecting) Icons.Filled.Delete else Icons.Filled.Add,
            contentDescription = stringResource(
                if (isSelecting) R.string.feature_all_exercises_bulk_archive
                else R.string.feature_all_exercises_fab_create,
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
    )
}

private fun LazyPagingItems<*>.isEmptyAndIdle(): Boolean =
    itemCount == 0 &&
        loadState.refresh is LoadState.NotLoading &&
        loadState.append is LoadState.NotLoading &&
        loadState.prepend is LoadState.NotLoading
