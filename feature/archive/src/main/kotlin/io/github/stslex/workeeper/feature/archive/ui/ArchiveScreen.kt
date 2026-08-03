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
            // The count formatting moved off the UI: CLAUDE.md puts display strings in the UI
            // mapper, and the `TODO(tech-debt-localization)` that used to sit here said so. The
            // labels arrive pre-formatted on State; `ArchiveSegmentLabelTest` asserts the
            // composition, which nothing else could — a golden of a segmented control cannot say
            // whether its own count is right.
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
    // Taken ABOVE the swap, not inside the region: this branch decides whether the region is
    // composed at all, and the hold works by staying in composition after the data stops loading.
    // Reading `archiveListSurface` here would delete the region — and the deferral with it — at
    // the exact moment the minimum starts. See [ListBody].
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
        // Full-bleed, like both siblings: the drawn row owns its own gutter and its own rule, so
        // the list adds no horizontal padding and no inter-item spacing. **No bottom clearance
        // either** — `#s-list`'s navnote scopes the 88dp to the screens that draw a FAB
        // ("Запас нужен только тем экранам, где кнопка есть"), and this screen draws none.
        modifier = modifier.testTag("ArchiveExerciseList"),
        state = rememberLazyListState(),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.item?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { row ->
                ArchivedItemRow(
                    // §26, continuity motion. This is the list where a row leaves on a user
                    // action — restore and permanent delete both remove one — and until now the
                    // remainder jumped up under the finger that pressed. Same spec as both list
                    // screens; the class has one duration and one curve.
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = row.item,
                    metaLine = row.metaLine,
                    // The drawing removes the last row's rule (`.frame .row:last-of-type`), so the
                    // list does not end on a hairline into empty space.
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
        // Full-bleed, like both siblings: the drawn row owns its own gutter and its own rule, so
        // the list adds no horizontal padding and no inter-item spacing. **No bottom clearance
        // either** — `#s-list`'s navnote scopes the 88dp to the screens that draw a FAB
        // ("Запас нужен только тем экранам, где кнопка есть"), and this screen draws none.
        modifier = modifier.testTag("ArchiveTrainingList"),
        state = rememberLazyListState(),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.item?.uuid ?: "training_$index" },
        ) { index ->
            items[index]?.let { row ->
                ArchivedItemRow(
                    // §26, continuity motion. This is the list where a row leaves on a user
                    // action — restore and permanent delete both remove one — and until now the
                    // remainder jumped up under the finger that pressed. Same spec as both list
                    // screens; the class has one duration and one curve.
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = row.item,
                    metaLine = row.metaLine,
                    // The drawing removes the last row's rule (`.frame .row:last-of-type`), so the
                    // list does not end on a hairline into empty space.
                    showDivider = index < items.itemCount - 1,
                    onRestore = { consume(Action.Click.OnRestoreClick(row.item)) },
                    onPermanentDelete = { consume(Action.Click.OnPermanentDeleteClick(row.item)) },
                )
            }
        }
        pagingTail(items = items, onRetry = { items.retry() })
    }
}

/**
 * §26 "Paging tails" — built here for the first time, and the reason is recorded rather than
 * assumed: this screen pages (`ExerciseRepositoryImpl.pagedArchived()` is a real `Pager`) and has
 * always paged, but it read `loadState.append` **nowhere except an emptiness predicate**, so a
 * failed page was a list that quietly stopped. See `archive-delta.md` §3.1 — the drawing was never
 * at fault and no correction is owed to it; the screen was simply behind.
 *
 * The decision lives in [pagingTailKind], not here, because no golden can see it. This is dispatch.
 */
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
    surface: ArchiveListSurface?,
    items: LazyPagingItems<*>,
    supportingText: String,
    emptyTestTag: String,
    modifier: Modifier = Modifier,
) {
    // The verdict is PASSED IN, from the swap above — see [ArchiveBody]. Deriving it here again
    // would put the deferral inside the composable the raw verdict removes, which is where the
    // minimum hold used to die. `null` is the deferral window, where nothing draws at all.
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
