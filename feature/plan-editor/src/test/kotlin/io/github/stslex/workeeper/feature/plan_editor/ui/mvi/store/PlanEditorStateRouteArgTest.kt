// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the `Screen.PlanEditor` route → `State.Mode` mapping that
 * `PlanEditorStoreImpl` uses to seed initial state. The Store calls
 * `screen.toInitialState()` with the assisted-injected screen, so this test pins the
 * routing branches:
 *
 *   - [Screen.PlanEditor.Existing] with `performedExerciseUuid != null` OR `trainingUuid`
 *     non-blank → `Mode.PerformedExercise`.
 *   - Otherwise (`exerciseUuid` only) → `Mode.Exercise`.
 *   - `exerciseUuid == null` is invalid input — the helper throws `IllegalStateException`.
 *   - [Screen.PlanEditor.Draft] → `Mode.Draft`; the seed `(initialType, initialPlanJson)`
 *     hydrates the working draft so the editor renders without a DB load.
 */
internal class PlanEditorStateRouteArgTest {

    @Test
    fun `live workout entry maps to PerformedExercise mode`() {
        val screen = Screen.PlanEditor.Existing(
            performedExerciseUuid = "performed-1",
            exerciseUuid = "ex-1",
            trainingUuid = "training-1",
        )

        val mode = screen.toMode()

        assertEquals(
            State.Mode.PerformedExercise(
                performedExerciseUuid = "performed-1",
                exerciseUuid = "ex-1",
                trainingUuid = "training-1",
            ),
            mode,
        )
    }

    @Test
    fun `single-training edit entry maps to PerformedExercise mode without performed uuid`() {
        val screen = Screen.PlanEditor.Existing(
            performedExerciseUuid = null,
            exerciseUuid = "ex-1",
            trainingUuid = "training-1",
        )

        val mode = screen.toMode()

        assertEquals(
            State.Mode.PerformedExercise(
                performedExerciseUuid = null,
                exerciseUuid = "ex-1",
                trainingUuid = "training-1",
            ),
            mode,
        )
    }

    @Test
    fun `live workout adhoc entry maps to PerformedExercise mode without training uuid`() {
        val screen = Screen.PlanEditor.Existing(
            performedExerciseUuid = "performed-1",
            exerciseUuid = "ex-1",
            trainingUuid = null,
        )

        val mode = screen.toMode()

        assertEquals(
            State.Mode.PerformedExercise(
                performedExerciseUuid = "performed-1",
                exerciseUuid = "ex-1",
                trainingUuid = null,
            ),
            mode,
        )
    }

    @Test
    fun `exercise default plan entry maps to Exercise mode`() {
        val screen = Screen.PlanEditor.Existing(
            performedExerciseUuid = null,
            exerciseUuid = "ex-1",
            trainingUuid = null,
        )

        val mode = screen.toMode()

        assertEquals(State.Mode.Exercise(exerciseUuid = "ex-1"), mode)
    }

    @Test
    fun `null exerciseUuid is rejected because the editor needs an exercise to load against`() {
        val screen = Screen.PlanEditor.Existing(
            performedExerciseUuid = "performed-1",
            exerciseUuid = null,
            trainingUuid = "training-1",
        )

        assertThrows(IllegalStateException::class.java) { screen.toMode() }
    }

    @Test
    fun `blank trainingUuid falls through to Exercise mode rather than PerformedExercise`() {
        val screen = Screen.PlanEditor.Existing(
            performedExerciseUuid = null,
            exerciseUuid = "ex-1",
            trainingUuid = "",
        )

        val mode = screen.toMode()

        assertEquals(State.Mode.Exercise(exerciseUuid = "ex-1"), mode)
    }

    @Test
    fun `Draft route maps to Mode Draft and hydrates seed type`() {
        val screen = Screen.PlanEditor.Draft(
            initialType = ExerciseTypeUiModel.WEIGHTLESS,
            initialPlanJson = null,
        )

        val state = screen.toInitialState()

        assertEquals(State.Mode.Draft, state.mode)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, state.type)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, state.initialType)
        assertTrue(state.draft.isEmpty())
        // Draft mode skips the DB load — the editor renders immediately.
        assertFalse(state.isLoading)
    }

    @Test
    fun `Draft route hydrates seed plan when initialPlanJson is present`() {
        val seed = listOf(
            PlanSetUiModel(weight = 70.0, reps = 8, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
        )
        val screen = Screen.PlanEditor.Draft(
            initialType = ExerciseTypeUiModel.WEIGHTED,
            initialPlanJson = Json.encodeToString(seed),
        )

        val state = screen.toInitialState()

        assertEquals(State.Mode.Draft, state.mode)
        assertEquals(seed, state.draft.toList())
        assertEquals(seed, state.initialDraft.toList())
        assertEquals(ExerciseTypeUiModel.WEIGHTED, state.type)
    }
}
