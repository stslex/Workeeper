// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.collectAsItems
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.ListBody
import io.github.stslex.workeeper.core.ui.kit.components.paging.listBody
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedControl
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec
import io.github.stslex.workeeper.core.ui.kit.theme.continuityPositionalSpec
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.ui.components.ArchiveListSurface
import io.github.stslex.workeeper.feature.archive.ui.components.ArchivedItemRow
import io.github.stslex.workeeper.feature.archive.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.archive.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.archive.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.archive.ui.components.PermanentDeleteDialog
import io.github.stslex.workeeper.feature.archive.ui.components.pagingTailKind
import io.github.stslex.workeeper.feature.archive.ui.components.rememberArchiveSurface
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun ArchiveScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exerciseItems = state.archivedExercisesPaging.collectAsItems()

    val trainingItems = state.archivedTrainingsPaging.collectAsItems()

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
            items = persistentListOf(state.exerciseSegmentLabel, state.trainingSegmentLabel),
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
    // GUARD: read the surface above the swap — deriving it inside the region kills the deferral.
    val surface = rememberArchiveSurface(items)
    if (listBody(surface, ArchiveListSurface.CONTENT) == ListBody.REGION) {
        ArchiveEmptyRegion(
            modifier = modifier,
            surface = surface,
            items = items,
            supportingText = stringResource(R.string.feature_archive_empty_supporting_exercises),
            emptyTestTag = "ArchiveEmptyExercises",
        )
        return
    }
    LazyColumn(
        // Full-bleed: the row owns its gutter and rule; no bottom clearance without a FAB.
        modifier = modifier.testTag("ArchiveExerciseList"),
        state = rememberLazyListState(),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.item?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { row ->
                ArchivedItemRow(
                    // Continuity motion (§26): a row leaves on restore or permanent delete.
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = row.item,
                    metaLine = row.metaLine,
                    // No rule under the last row: the list must not end on a hairline.
                    showDivider = index < items.itemCount - 1,
                    onRestore = { consume(Action.Click.OnRestoreClick(row.item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(row.item)) },
                )
            }
        }
        pagingTail(items = items, onRetry = { items.retry() })
    }
}

@Composable
private fun ArchivedTrainingList(
    items: LazyPagingItems<ArchivedItemUi.Training>,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same gate as the exercise tab, and same reason — see [ListBody].
    val surface = rememberArchiveSurface(items)
    if (listBody(surface, ArchiveListSurface.CONTENT) == ListBody.REGION) {
        ArchiveEmptyRegion(
            modifier = modifier,
            surface = surface,
            items = items,
            supportingText = stringResource(R.string.feature_archive_empty_supporting_trainings),
            emptyTestTag = "ArchiveEmptyTrainings",
        )
        return
    }
    LazyColumn(
        // Full-bleed: the row owns its gutter and rule; no bottom clearance without a FAB.
        modifier = modifier.testTag("ArchiveTrainingList"),
        state = rememberLazyListState(),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.item?.uuid ?: "training_$index" },
        ) { index ->
            items[index]?.let { row ->
                ArchivedItemRow(
                    // Continuity motion (§26): a row leaves on restore or permanent delete.
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = row.item,
                    metaLine = row.metaLine,
                    // No rule under the last row: the list must not end on a hairline.
                    showDivider = index < items.itemCount - 1,
                    onRestore = { consume(Action.Click.OnRestoreClick(row.item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(row.item)) },
                )
            }
        }
        pagingTail(items = items, onRetry = { items.retry() })
    }
}

/** Dispatches the append tail; the decision lives in [pagingTailKind], where a test can see it. */
private fun LazyListScope.pagingTail(
    items: LazyPagingItems<*>,
    onRetry: () -> Unit,
) {
    when (pagingTailKind(items.loadState.append)) {
        PagingTailKind.LOADING -> item(key = "paging_loading") { PagingLoadingFooter() }
        PagingTailKind.ERROR -> item(key = "paging_error") { PagingErrorFooter(onRetry = onRetry) }
        PagingTailKind.NONE -> Unit
    }
}

/**
 * The empty region — four verdicts, not the siblings' six: no tag filter, no selection mode here.
 */
@Composable
private fun ArchiveEmptyRegion(
    surface: ArchiveListSurface?,
    items: LazyPagingItems<*>,
    supportingText: String,
    emptyTestTag: String,
    modifier: Modifier = Modifier,
) {
    // Passed in from the swap above, never re-derived here; `null` is the deferral window.
    when (surface) {
        null -> Unit

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
                exerciseSegmentLabel = "Упражнения (0)",
                trainingSegmentLabel = "Тренировки (0)",
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
