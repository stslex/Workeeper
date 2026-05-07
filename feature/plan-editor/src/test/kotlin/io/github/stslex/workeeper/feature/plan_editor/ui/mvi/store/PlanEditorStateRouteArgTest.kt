// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Verifies the `Screen.PlanEditor` route → `State.Mode` mapping that
 * `PlanEditorStoreImpl` uses to seed initial state. The Store calls
 * `State.init(screen.toMode())` with the assisted-injected screen, so this test
 * pins the routing branches:
 *
 *   - `performedExerciseUuid != null` OR `trainingUuid` non-blank → `Mode.PerformedExercise`.
 *   - Otherwise (`exerciseUuid` only) → `Mode.Exercise`.
 *   - `exerciseUuid == null` is invalid input — the helper throws `IllegalStateException`.
 */
internal class PlanEditorStateRouteArgTest {

    @Test
    fun `live workout entry maps to PerformedExercise mode`() {
        val screen = Screen.PlanEditor(
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
        val screen = Screen.PlanEditor(
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
        val screen = Screen.PlanEditor(
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
        val screen = Screen.PlanEditor(
            performedExerciseUuid = null,
            exerciseUuid = "ex-1",
            trainingUuid = null,
        )

        val mode = screen.toMode()

        assertEquals(State.Mode.Exercise(exerciseUuid = "ex-1"), mode)
    }

    @Test
    fun `null exerciseUuid is rejected because the editor needs an exercise to load against`() {
        val screen = Screen.PlanEditor(
            performedExerciseUuid = "performed-1",
            exerciseUuid = null,
            trainingUuid = "training-1",
        )

        assertThrows(IllegalStateException::class.java) { screen.toMode() }
    }

    @Test
    fun `blank trainingUuid falls through to Exercise mode rather than PerformedExercise`() {
        val screen = Screen.PlanEditor(
            performedExerciseUuid = null,
            exerciseUuid = "ex-1",
            trainingUuid = "",
        )

        val mode = screen.toMode()

        assertEquals(State.Mode.Exercise(exerciseUuid = "ex-1"), mode)
    }
}
