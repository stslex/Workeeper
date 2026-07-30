// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui

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
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedControl
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.ui.components.ArchiveListSurface
import io.github.stslex.workeeper.feature.archive.ui.components.ArchivedItemRow
import io.github.stslex.workeeper.feature.archive.ui.components.PermanentDeleteDialog
import io.github.stslex.workeeper.feature.archive.ui.components.archiveListSurface
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun ArchiveScreen(
    state: State,
    consume: (Action) -> Unit,
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
            title = stringResource(R.string.feature_archive_title),
            navigationIcon = {
                IconButton(
                    modifier = Modifier.testTag("ArchiveBackButton"),
                    onClick = { consume(Action.Navigation.Back) },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconMd),
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(KitR.string.core_ui_kit_action_back),
                    )
                }
            },
        )

        AppSegmentedControl(
            modifier = Modifier
                .padding(horizontal = AppDimension.screenEdge, vertical = AppDimension.Space.sm)
                .testTag("ArchiveSegments"),
            // TODO(tech-debt-localization): Move segment label formatting with counts into
            // Archive state mapping to keep UI text rendering-only.
            items = persistentListOf(
                stringResource(R.string.feature_archive_segment_exercises, state.exerciseCount),
                stringResource(R.string.feature_archive_segment_trainings, state.trainingCount),
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
                modifier = Modifier.fillMaxSize(),
            )

            Segment.TRAININGS -> ArchivedTrainingList(
                items = trainingItems,
                consume = consume,
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
    items: LazyPagingItems<ArchivedItemUi.Exercise>,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (archiveListSurface(items.itemCount, items.loadState) != ArchiveListSurface.CONTENT) {
        ArchiveEmptyRegion(
            modifier = modifier,
            items = items,
            supportingText = stringResource(R.string.feature_archive_empty_supporting_exercises),
            emptyTestTag = "ArchiveEmptyExercises",
        )
        return
    }
    LazyColumn(
        modifier = modifier.testTag("ArchiveExerciseList"),
        contentPadding = PaddingValues(
            horizontal = AppDimension.screenEdge,
            vertical = AppDimension.Space.sm,
        ),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.item?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { row ->
                ArchivedItemRow(
                    item = row.item,
                    archivedAtLabel = row.archivedAtLabel,
                    onRestore = { consume(Action.Click.OnRestoreClick(row.item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(row.item)) },
                )
            }
        }
    }
}

@Composable
private fun ArchivedTrainingList(
    items: LazyPagingItems<ArchivedItemUi.Training>,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (archiveListSurface(items.itemCount, items.loadState) != ArchiveListSurface.CONTENT) {
        ArchiveEmptyRegion(
            modifier = modifier,
            items = items,
            supportingText = stringResource(R.string.feature_archive_empty_supporting_trainings),
            emptyTestTag = "ArchiveEmptyTrainings",
        )
        return
    }
    LazyColumn(
        modifier = modifier.testTag("ArchiveTrainingList"),
        contentPadding = PaddingValues(
            horizontal = AppDimension.screenEdge,
            vertical = AppDimension.Space.sm,
        ),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.item?.uuid ?: "training_$index" },
        ) { index ->
            items[index]?.let { row ->
                ArchivedItemRow(
                    item = row.item,
                    archivedAtLabel = row.archivedAtLabel,
                    onRestore = { consume(Action.Click.OnRestoreClick(row.item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(row.item)) },
                )
            }
        }
    }
}

/**
 * The empty region — B22's fix for this screen.
 *
 * `isPagingEmpty` collapsed three states into one: it wanted `refresh`, `append` **and** `prepend`
 * all `NotLoading`, so on a cold open the tab had no rows *and* its empty state was suppressed by
 * the same condition, and drew nothing at all. A failed first page blanked identically.
 *
 * Four verdicts, not the siblings' six: this screen has no tag filter and no selection mode, so
 * those verdicts are unreachable and are not declared. The loading and error treatments are the
 * kit's paging tails at the position row 1 will occupy — placement, not a new drawing, exactly as
 * on the list screens.
 */
@Composable
private fun ArchiveEmptyRegion(
    items: LazyPagingItems<*>,
    supportingText: String,
    emptyTestTag: String,
    modifier: Modifier = Modifier,
) {
    when (archiveListSurface(items.itemCount, items.loadState)) {
        ArchiveListSurface.CONTENT -> Unit

        ArchiveListSurface.LOADING -> AppPagingLoadingFooter(
            modifier = modifier.testTag("ArchiveColdOpen"),
            label = stringResource(R.string.feature_archive_paging_loading),
        )

        ArchiveListSurface.REFRESH_ERROR -> AppPagingErrorFooter(
            modifier = modifier.testTag("ArchiveColdOpenError"),
            reason = stringResource(R.string.feature_archive_refresh_error),
            retryLabel = stringResource(R.string.feature_archive_paging_retry),
            onRetry = { items.retry() },
            ruled = false,
        )

        ArchiveListSurface.EMPTY -> AppEmptyState(
            modifier = modifier.testTag(emptyTestTag),
            headline = stringResource(R.string.feature_archive_empty_headline),
            supportingText = supportingText,
            icon = Icons.Filled.Inventory2,
        )
    }
}

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
        )
    }
}
