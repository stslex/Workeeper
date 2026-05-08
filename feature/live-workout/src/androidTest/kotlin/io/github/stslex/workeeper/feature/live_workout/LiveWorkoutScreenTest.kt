// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.ui.LiveWorkoutScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for `LiveWorkoutScreen` covering the action-dispatch surface that the
 * draft / visible-row refactor touches. Drives the screen with a fixed `State` and
 * asserts that user gestures route through the right `Action` variants. State
 * mutation is the store's job and is covered by handler / mapper / mutator tests —
 * this test is intentionally about the UI ↔ MVI boundary.
 */
@Smoke
@RunWith(AndroidJUnit4::class)
internal class LiveWorkoutScreenTest : BaseComposeTest() {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screen_renders_with_expected_root_test_tag() {
        composeRule.setThemedContent {
            LiveWorkoutScreen(state = stateWithOnePlanSet(), consume = {})
        }

        composeRule.onNodeWithTag("LiveWorkoutScreen").assertIsDisplayed()
    }

    @Test
    fun finish_button_dispatches_OnFinishClick() {
        val capture = createActionCapture<Action>()
        composeRule.setThemedContent {
            LiveWorkoutScreen(state = stateWithOnePlanSet(), consume = capture)
        }

        composeRule.onNodeWithTag("LiveWorkoutFinishButton").performClick()

        capture.capturedFirst<Action.Click.OnFinishClick>()
    }

    @Test
    fun add_set_button_dispatches_OnAddSet_with_exercise_uuid() {
        val capture = createActionCapture<Action>()
        composeRule.setThemedContent {
            LiveWorkoutScreen(state = stateWithOnePlanSet(), consume = capture)
        }

        composeRule.onNodeWithTag("LiveExerciseCard_AddSet_$PE_UUID").performClick()

        val action = capture.capturedFirst<Action.Click.OnAddSet>()
        assertEquals(PE_UUID, action.performedExerciseUuid)
    }

    @Test
    fun type_chip_click_dispatches_OnSetTypeSelect_with_current_chip_type() {
        // Plan type=WORK at position 0; clicking the chip carries the current chip type
        // (WORK). The handler advances via `.next()` — that side of the contract is
        // covered by `LiveSetDraftBehaviorTest`. This test locks the UI half: the action
        // carries the row's current type.
        val capture = createActionCapture<Action>()
        composeRule.setThemedContent {
            LiveWorkoutScreen(state = stateWithOnePlanSet(), consume = capture)
        }

        composeRule.onNodeWithTag("LiveSetRow_${PE_UUID}_0_TypeChip").performClick()

        val action = capture.capturedFirst<Action.Click.OnSetTypeSelect>()
        assertEquals(PE_UUID, action.performedExerciseUuid)
        assertEquals(0, action.position)
        assertEquals(
            "Chip click must carry the current row's type (WORK)",
            SetTypeUiModel.WORK,
            action.type,
        )
    }

    @Test
    fun checkbox_click_on_undone_row_dispatches_OnSetMarkDone() {
        val capture = createActionCapture<Action>()
        composeRule.setThemedContent {
            LiveWorkoutScreen(state = stateWithOnePlanSet(), consume = capture)
        }

        composeRule.onNodeWithTag("LiveSetRow_${PE_UUID}_0_Checkbox").performClick()

        val action = capture.capturedFirst<Action.Click.OnSetMarkDone>()
        assertEquals(PE_UUID, action.performedExerciseUuid)
        assertEquals(0, action.position)
    }

    @Test
    fun checkbox_click_on_done_row_dispatches_OnSetUncheck() {
        val capture = createActionCapture<Action>()
        composeRule.setThemedContent {
            LiveWorkoutScreen(state = stateWithOneDoneSet(), consume = capture)
        }

        composeRule.onNodeWithTag("LiveSetRow_${PE_UUID}_0_Checkbox").performClick()

        val action = capture.capturedFirst<Action.Click.OnSetUncheck>()
        assertEquals(PE_UUID, action.performedExerciseUuid)
        assertEquals(0, action.position)
    }

    /**
     * Wraps the rendered widget in `AppTheme` so `LocalAppColors` is provided. Without
     * this, every read of `AppUi.colors.*` inside the screen throws at composition.
     */
    private fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
        setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) { content() }
        }
    }

    private fun stateWithOnePlanSet(): State = baseState(
        exercises = persistentListOf(exerciseWithOneRow(isDone = false)),
    )

    private fun stateWithOneDoneSet(): State = baseState(
        exercises = persistentListOf(exerciseWithOneRow(isDone = true)),
    )

    private fun baseState(
        exercises: kotlinx.collections.immutable.ImmutableList<LiveExerciseUiModel>,
    ): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        trainingName = "Push Day",
        trainingNameLabel = "Push Day",
        trainingNameDraft = "Push Day",
        nowMillis = 60_000L,
        elapsedDurationLabel = "01:00",
        totalCount = 1,
        progressLabel = "0 of 1 done",
        exercises = exercises,
        // The card body (set rows + Add-set CTA) only renders when the exercise is in
        // `expandedExerciseUuids`. The store seeds this from `activeExerciseUuids` at
        // load time; the test bypasses load and must opt the row in explicitly.
        expandedExerciseUuids = persistentSetOf(PE_UUID),
        isLoading = false,
    )

    private fun exerciseWithOneRow(isDone: Boolean): LiveExerciseUiModel {
        val row = LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = isDone,
        )
        return LiveExerciseUiModel(
            performedExerciseUuid = PE_UUID,
            exerciseUuid = "ex-1",
            exerciseName = "Bench Press",
            exerciseType = ExerciseTypeUiModel.WEIGHTED,
            position = 0,
            status = ExerciseStatusUiModel.CURRENT,
            statusLabel = "Plan: 1x5",
            planSets = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performedSets = if (isDone) persistentListOf(row) else persistentListOf(),
            visibleSets = persistentListOf(row),
        )
    }

    private companion object {
        const val PE_UUID = "pe-1"
    }
}
