// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedControl
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.settings.ui.components.ArchivedItemRow
import io.github.stslex.workeeper.feature.settings.ui.components.PermanentDeleteDialog
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun ArchiveScreen(
    state: State,
    consume: (Action) -> Unit,
    formatArchivedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val exerciseItems = remember(state.archivedExercisesPaging) {
        state.archivedExercisesPaging()
    }.collectAsLazyPagingItems()

    val trainingItems = remember(state.archivedTrainingsPaging) {
        state.archivedTrainingsPaging()
    }.collectAsLazyPagingItems()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("ArchiveScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(R.string.feature_settings_archive_title),
            navigationIcon = {
                IconButton(
                    modifier = Modifier.testTag("ArchiveBackButton"),
                    onClick = { consume(Action.Navigation.Back) },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconMd),
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feature_settings_back),
                    )
                }
            },
        )

        AppSegmentedControl(
            modifier = Modifier
                .padding(horizontal = AppDimension.screenEdge, vertical = AppDimension.Space.sm)
                .testTag("ArchiveSegments"),
            items = persistentListOf(
                stringResource(R.string.feature_settings_archive_segment_exercises, state.exerciseCount),
                stringResource(R.string.feature_settings_archive_segment_trainings, state.trainingCount),
            ),
            selected = if (state.selectedSegment == Segment.EXERCISES) 0 else 1,
            onSelectedChange = { index ->
                val segment = if (index == 0) Segment.EXERCISES else Segment.TRAININGS
                consume(Action.Click.OnSegmentChange(segment))
            },
        )

        when (state.selectedSegment) {
            Segment.EXERCISES -> ArchivedExerciseList(
                items = exerciseItems,
                consume = consume,
                formatArchivedAt = formatArchivedAt,
                modifier = Modifier.fillMaxSize(),
            )

            Segment.TRAININGS -> ArchivedTrainingList(
                items = trainingItems,
                consume = consume,
                formatArchivedAt = formatArchivedAt,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val target = state.pendingDeleteTarget
    if (target != null) {
        if (state.deleteImpactLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ArchiveDialogLoading"),
                contentAlignment = Alignment.Center,
            ) {
                AppLoadingIndicator()
            }
        } else {
            PermanentDeleteDialog(
                target = target,
                impactCount = state.pendingDeleteImpact ?: 0,
                onConfirm = { consume(Action.Click.OnDeleteConfirm) },
                onDismiss = { consume(Action.Click.OnDeleteDismiss) },
            )
        }
    }
}

@Composable
private fun ArchivedExerciseList(
    items: LazyPagingItems<ArchivedItem.Exercise>,
    consume: (Action) -> Unit,
    formatArchivedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (isPagingEmpty(items.loadState, items.itemCount)) {
        AppEmptyState(
            modifier = modifier.testTag("ArchiveEmptyExercises"),
            headline = stringResource(R.string.feature_settings_archive_empty_headline),
            supportingText = stringResource(R.string.feature_settings_archive_empty_supporting_exercises),
            icon = Icons.Filled.Inventory2,
        )
        return
    }
    LazyColumn(
        modifier = modifier.testTag("ArchiveExerciseList"),
        contentPadding = PaddingValues(horizontal = AppDimension.screenEdge, vertical = AppDimension.Space.sm),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { item ->
                ArchivedItemRow(
                    item = item,
                    archivedAtLabel = formatArchivedAt(item.archivedAt),
                    onRestore = { consume(Action.Click.OnRestoreClick(item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(item)) },
                )
            }
        }
    }
}

@Composable
private fun ArchivedTrainingList(
    items: LazyPagingItems<ArchivedItem.Training>,
    consume: (Action) -> Unit,
    formatArchivedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (isPagingEmpty(items.loadState, items.itemCount)) {
        AppEmptyState(
            modifier = modifier.testTag("ArchiveEmptyTrainings"),
            headline = stringResource(R.string.feature_settings_archive_empty_headline),
            supportingText = stringResource(R.string.feature_settings_archive_empty_supporting_trainings),
            icon = Icons.Filled.Inventory2,
        )
        return
    }
    LazyColumn(
        modifier = modifier.testTag("ArchiveTrainingList"),
        contentPadding = PaddingValues(horizontal = AppDimension.screenEdge, vertical = AppDimension.Space.sm),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.uuid ?: "training_$index" },
        ) { index ->
            items[index]?.let { item ->
                ArchivedItemRow(
                    item = item,
                    archivedAtLabel = formatArchivedAt(item.archivedAt),
                    onRestore = { consume(Action.Click.OnRestoreClick(item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(item)) },
                )
            }
        }
    }
}

@Suppress("ComplexCondition")
private fun isPagingEmpty(
    loadState: androidx.paging.CombinedLoadStates,
    itemCount: Int,
): Boolean = loadState.refresh is LoadState.NotLoading &&
    loadState.append is LoadState.NotLoading &&
    loadState.prepend is LoadState.NotLoading &&
    itemCount == 0

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ArchiveScreenPreview() {
    AppTheme {
        ArchiveScreen(
            state = State(
                selectedSegment = Segment.EXERCISES,
                exerciseCount = 0,
                trainingCount = 0,
                archivedExercisesPaging = { flowOf(PagingData.empty()) },
                archivedTrainingsPaging = { flowOf(PagingData.empty()) },
                pendingDeleteImpact = null,
                pendingDeleteTarget = null,
                deleteImpactLoading = false,
            ),
            consume = {},
            formatArchivedAt = { "Archived recently" },
        )
    }
}
