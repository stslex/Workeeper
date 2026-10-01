// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.archive

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_back
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveMetaCopy
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveUiMapper.toUi
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_action_more
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_action_permanent_delete
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_action_restore
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_confirm_delete
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_impact_summary_empty
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_permanent_delete_body_no_history
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_permanent_delete_body_with_history
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_permanent_delete_title
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_empty_headline
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_empty_supporting_exercises
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_empty_supporting_trainings
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_kind_exercise
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_kind_training
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_label_archived
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_label_archived_since_format
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_meta_separator
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_paging_error
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_paging_loading
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_paging_retry
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_refresh_error
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_segment_exercises
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_segment_trainings
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_session_count
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_snackbar_deleted_format
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_snackbar_restored_format
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_snackbar_undo
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_title
import io.github.stslex.workeeper.feature.archive.ui.ArchiveScreen
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.feature.archive.resources.Res as ArchiveRes

class ArchiveFeatureSceneIosTest {

    @Test
    fun resourcesPagingBranchesAndActionsRenderAndDispatch() = runComposeUiTest {
        assertEquals(EXPECTED_CATALOG, featureCatalog())

        val exercise = ArchivedItem.Exercise(
            uuid = "native-exercise",
            name = "Bench press",
            tags = listOf("chest"),
            archivedAt = ARCHIVED_AT,
            type = ExerciseTypeDomain.WEIGHTED,
        )
        val training = ArchivedItem.Training(
            uuid = "native-training",
            name = "Upper body",
            tags = emptyList(),
            archivedAt = 0L,
            exerciseCount = 8,
        )
        val copy = runBlocking {
            ArchiveMetaCopy(
                exerciseKind = getString(ArchiveRes.string.feature_archive_kind_exercise),
                trainingKind = getString(ArchiveRes.string.feature_archive_kind_training),
                archived = getString(ArchiveRes.string.feature_archive_label_archived),
                separator = getString(ArchiveRes.string.feature_archive_meta_separator),
            )
        }
        val archivedSince = runBlocking {
            getString(ArchiveRes.string.feature_archive_label_archived_since_format, "3 July")
        }
        val exerciseRow = exercise.toUi(copy, archivedSince)
        val trainingRow = training.toUi(copy, archivedSince)
        assertEquals("exercise · archived since 3 July · chest", exerciseRow.metaLine)
        assertEquals("training · archived", trainingRow.metaLine)

        val exerciseLabel = runBlocking {
            getString(ArchiveRes.string.feature_archive_segment_exercises, 1)
        }
        val trainingLabel = runBlocking {
            getString(ArchiveRes.string.feature_archive_segment_trainings, 1)
        }
        assertEquals(listOf("Exercises (1)", "Trainings (1)"), listOf(exerciseLabel, trainingLabel))

        val settledState = State(
            selectedSegment = Segment.EXERCISES,
            exerciseCount = 1,
            trainingCount = 1,
            exerciseSegmentLabel = exerciseLabel,
            trainingSegmentLabel = trainingLabel,
            archivedExercisesPaging = paging(PagingData.empty(sourceLoadStates = settled())),
            archivedTrainingsPaging = paging(PagingData.empty(sourceLoadStates = settled())),
            pendingDeleteImpact = null,
            pendingDeleteTarget = null,
            deleteImpactLoading = false,
        )
        val state = mutableStateOf(settledState)
        val actions = mutableListOf<Action>()

        setContent {
            AppTheme {
                ArchiveScreen(
                    state = state.value,
                    consume = actions::add,
                )
            }
        }
        settleUntilTag("ArchiveEmptyExercises")

        // Settled empty exercises, both segment labels, title and the kit back affordance.
        onNodeWithTag("ArchiveScreen", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Archive", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription(kitString { getString(KitRes.string.core_ui_kit_action_back) })
            .assertIsDisplayed()
        onNodeWithText(exerciseLabel, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(trainingLabel, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ArchiveEmptyExercises", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Nothing archived", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "Archived exercises appear here for restore or permanent delete.",
            useUnmergedTree = true,
        ).assertIsDisplayed()

        // Segment selection dispatches; the trainings branch renders its own empty state.
        onNodeWithText(trainingLabel).performClick()
        assertEquals(listOf<Action>(Action.Click.OnSegmentChange(Segment.TRAININGS)), actions)
        state.value = settledState.copy(selectedSegment = Segment.TRAININGS)
        settleUntilTag("ArchiveEmptyTrainings")
        onNodeWithTag("ArchiveEmptyTrainings", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "Archived trainings appear here for restore or permanent delete.",
            useUnmergedTree = true,
        ).assertIsDisplayed()

        onNodeWithTag("ArchiveBackButton").performClick()
        assertEquals(Action.Navigation.Back, actions.last())

        // An unsettled first page is the deferred loading verdict, a failed one its own verdict.
        state.value = settledState.copy(
            archivedExercisesPaging = paging(
                PagingData.empty(sourceLoadStates = settled(refresh = LoadState.Loading)),
            ),
        )
        settleUntilTag("ArchiveColdOpen")
        // Held, not transient: a fresh list also starts at Loading, so outlast that first frame.
        repeat(STABLE_SETTLE_STEPS) { settleScene() }
        onNodeWithTag("ArchiveColdOpen", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Loading", useUnmergedTree = true).assertIsDisplayed()

        state.value = settledState.copy(
            archivedExercisesPaging = paging(
                PagingData.empty(
                    sourceLoadStates = settled(
                        refresh = LoadState.Error(IllegalStateException("native refresh")),
                    ),
                ),
            ),
        )
        settleUntilTag("ArchiveColdOpenError")
        onNodeWithTag("ArchiveColdOpenError", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Couldn’t load the archive", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Retry", useUnmergedTree = true).assertIsDisplayed()

        // Populated exercise rows: name, composed meta line, restore and the overflow delete.
        val exerciseRows = settledState.copy(
            archivedExercisesPaging = paging(
                PagingData.from(listOf(exerciseRow), sourceLoadStates = settled()),
            ),
        )
        state.value = exerciseRows
        settleUntilTag("ArchiveExerciseList")
        onNodeWithTag("ArchiveExerciseList", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Bench press", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(exerciseRow.metaLine, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("More", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("ArchivedItemRestore_native-exercise").performClick()
        assertEquals(Action.Click.OnRestoreClick(exercise), actions.last())

        onNodeWithTag("ArchivedItemMenu_native-exercise").performClick()
        settleScene()
        onNodeWithText("Delete permanently").performClick()
        assertEquals(Action.Click.OnPermanentDeleteClick(exercise), actions.last())
        settleScene()

        // Both append tails: the loading footer, then the error footer with its retry.
        state.value = settledState.copy(
            archivedExercisesPaging = paging(
                PagingData.from(
                    listOf(exerciseRow),
                    sourceLoadStates = settled(append = LoadState.Loading),
                ),
            ),
        )
        settleUntilTag("ArchivePagingLoading")
        onNodeWithTag("ArchivePagingLoading", useUnmergedTree = true).assertIsDisplayed()

        state.value = settledState.copy(
            archivedExercisesPaging = paging(
                PagingData.from(
                    listOf(exerciseRow),
                    sourceLoadStates = settled(
                        append = LoadState.Error(IllegalStateException("native append")),
                    ),
                ),
            ),
        )
        settleUntilTag("ArchivePagingError")
        onNodeWithTag("ArchivePagingError", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Couldn’t load more", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ArchivePagingRetry", useUnmergedTree = true).assertIsDisplayed()

        // A populated training row restores through the same production row.
        state.value = settledState.copy(
            selectedSegment = Segment.TRAININGS,
            archivedTrainingsPaging = paging(
                PagingData.from(listOf(trainingRow), sourceLoadStates = settled()),
            ),
        )
        settleUntilTag("ArchiveTrainingList")
        onNodeWithTag("ArchiveTrainingList", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Upper body", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(trainingRow.metaLine, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ArchivedItemRestore_native-training").performClick()
        assertEquals(Action.Click.OnRestoreClick(training), actions.last())

        // The permanent-delete path: impact loading, then the production dialog with history.
        state.value = exerciseRows.copy(pendingDeleteTarget = exercise, deleteImpactLoading = true)
        settleUntilTag("ArchiveDialogLoading")
        onNodeWithTag("ArchiveDialogLoading", useUnmergedTree = true).assertIsDisplayed()

        state.value = exerciseRows.copy(pendingDeleteTarget = exercise, pendingDeleteImpact = 3)
        settleScene()
        onNodeWithText("Delete ‘Bench press’ permanently?", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("3 sessions", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "3 sessions of history will also be deleted. This action cannot be undone.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Delete").performClick()
        onNodeWithText(kitString { getString(KitRes.string.core_ui_kit_action_cancel) })
            .performClick()

        state.value = exerciseRows.copy(pendingDeleteTarget = exercise, pendingDeleteImpact = 1)
        settleScene()
        onNodeWithText("1 session", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "1 session of history will also be deleted. This action cannot be undone.",
            useUnmergedTree = true,
        ).assertIsDisplayed()

        state.value = exerciseRows.copy(pendingDeleteTarget = exercise, pendingDeleteImpact = 0)
        settleScene()
        onNodeWithText("No session history affected", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("This action cannot be undone.", useUnmergedTree = true).assertIsDisplayed()

        assertEquals(
            listOf(
                Action.Click.OnSegmentChange(Segment.TRAININGS),
                Action.Navigation.Back,
                Action.Click.OnRestoreClick(exercise),
                Action.Click.OnPermanentDeleteClick(exercise),
                Action.Click.OnRestoreClick(training),
                Action.Click.OnDeleteConfirm,
                Action.Click.OnDeleteDismiss,
            ),
            actions,
        )
    }

    private fun <T : Any> paging(data: PagingData<T>): PagingUiState<PagingData<T>> =
        PagingUiState { flowOf(data) }

    private fun settled(
        refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
        append: LoadState = LoadState.NotLoading(endOfPaginationReached = true),
    ): LoadStates = LoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = append,
    )

    private fun kitString(read: suspend () -> String): String = runBlocking { read() }

    private fun featureCatalog(): List<String> = runBlocking {
        listOf(
            getString(ArchiveRes.string.feature_archive_title),
            getString(ArchiveRes.string.feature_archive_action_more),
            getString(ArchiveRes.string.feature_archive_segment_exercises),
            getString(ArchiveRes.string.feature_archive_segment_trainings),
            getString(ArchiveRes.string.feature_archive_action_restore),
            getString(ArchiveRes.string.feature_archive_action_permanent_delete),
            getString(ArchiveRes.string.feature_archive_kind_exercise),
            getString(ArchiveRes.string.feature_archive_kind_training),
            getString(ArchiveRes.string.feature_archive_label_archived),
            getString(ArchiveRes.string.feature_archive_label_archived_since_format),
            getString(ArchiveRes.string.feature_archive_meta_separator),
            getString(ArchiveRes.string.feature_archive_empty_headline),
            getString(ArchiveRes.string.feature_archive_empty_supporting_exercises),
            getString(ArchiveRes.string.feature_archive_empty_supporting_trainings),
            getString(ArchiveRes.string.feature_archive_dialog_permanent_delete_title),
            getString(ArchiveRes.string.feature_archive_dialog_permanent_delete_body_no_history),
            getString(ArchiveRes.string.feature_archive_dialog_impact_summary_empty),
            getString(ArchiveRes.string.feature_archive_dialog_confirm_delete),
            getString(ArchiveRes.string.feature_archive_snackbar_restored_format),
            getString(ArchiveRes.string.feature_archive_snackbar_deleted_format),
            getString(ArchiveRes.string.feature_archive_snackbar_undo),
            getString(ArchiveRes.string.feature_archive_paging_loading),
            getString(ArchiveRes.string.feature_archive_paging_error),
            getString(ArchiveRes.string.feature_archive_paging_retry),
            getString(ArchiveRes.string.feature_archive_refresh_error),
            getPluralString(ArchiveRes.plurals.feature_archive_session_count, 1),
            getPluralString(ArchiveRes.plurals.feature_archive_session_count, 2),
            getPluralString(
                ArchiveRes.plurals.feature_archive_dialog_permanent_delete_body_with_history,
                1,
            ),
            getPluralString(
                ArchiveRes.plurals.feature_archive_dialog_permanent_delete_body_with_history,
                2,
            ),
        )
    }

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * Paging hands a new list over on the main dispatcher, and the kit then holds a shown loading
     * treatment for its minimum before the next verdict draws, so one fixed advance is not enough
     * per branch. Settles in bounded steps until the production [tag] that branch owns exists.
     */
    private fun ComposeUiTest.settleUntilTag(tag: String) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
        }
    }

    private companion object {

        /** Fixed so the scene never depends on the clock. */
        const val ARCHIVED_AT = 1_720_000_000_000L

        /** Ten virtual seconds: far beyond the kit's 140 ms deferral and 260 ms minimum hold. */
        const val MAX_SETTLE_STEPS = 10

        /** Settles that a transient first-frame verdict could not survive. */
        const val STABLE_SETTLE_STEPS = 3

        val EXPECTED_CATALOG = listOf(
            "Archive",
            "More",
            "Exercises (%1\$d)",
            "Trainings (%1\$d)",
            "Restore",
            "Delete permanently",
            "exercise",
            "training",
            "archived",
            "archived since %1\$s",
            "·",
            "Nothing archived",
            "Archived exercises appear here for restore or permanent delete.",
            "Archived trainings appear here for restore or permanent delete.",
            "Delete ‘%1\$s’ permanently?",
            "This action cannot be undone.",
            "No session history affected",
            "Delete",
            "%1\$s restored",
            "%1\$s permanently deleted",
            "Undo",
            "Loading",
            "Couldn’t load more",
            "Retry",
            "Couldn’t load the archive",
            "%1\$d session",
            "%1\$d sessions",
            "%1\$d session of history will also be deleted. This action cannot be undone.",
            "%1\$d sessions of history will also be deleted. This action cannot be undone.",
        )
    }
}
