// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.components.dialog.BlockedArchiveItem
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.ui.AllExercisesScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class AllExercisesScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TODO(feature-rewrite-tests): cover ExerciseRow click, FAB click, tag-filter toggle, and
    // empty state once Smoke harness wiring for AllExercisesScreen is restored.

    @Test
    @Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")
    fun pendingFeatureRewrite() {
        // Placeholder so AndroidJUnit4 has at least one @Test to discover.
    }

    /**
     * The blocked-archive path gives persistent, actionable feedback: a dialog naming each blocked
     * exercise and the trainings blocking it, dismissed only by acknowledging.
     */
    @Test
    @Regression
    fun blockedArchiveDialogListsBlockingTrainings() {
        val capture = createActionCapture<Action>()
        val state = baseState().copy(
            blockedArchiveDialog = State.BlockedArchiveDialog(
                archivedSummary = null,
                items = persistentListOf(
                    BlockedArchiveItem(
                        exerciseName = "Bench press",
                        trainingsLabel = "used in Push Day, Leg Day",
                    ),
                ),
            ),
        )

        composeTestRule.setContent {
            AppTheme {
                AllExercisesScreen(state = state, consume = capture)
            }
        }

        composeTestRule.onNodeWithText("Bench press").assertIsDisplayed()
        composeTestRule.onNodeWithText("used in Push Day, Leg Day").assertIsDisplayed()

        val confirmLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.feature_all_exercises_blocked_archive_confirm)
        composeTestRule.onNodeWithText(confirmLabel).performClick()

        capture.assertCapturedExactly(Action.Click.OnBlockedArchiveDismiss)
    }

    private fun baseState(): State = State(
        pagingUiState = PagingUiState { flowOf(PagingData.empty<ExerciseUiModel>()) },
        availableTags = persistentListOf(),
        activeTagFilter = persistentSetOf(),
        pendingPermanentDelete = null,
        selectionMode = State.SelectionMode.Off,
        pendingBulkDelete = null,
        blockedArchiveDialog = null,
    )
}
