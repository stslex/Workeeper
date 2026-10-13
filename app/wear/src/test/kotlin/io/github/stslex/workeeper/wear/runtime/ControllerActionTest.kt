// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** wear-phase-2-exercise-list.md §7.4: no `ControllerAction` prints a value, a name or the selection. */
internal class ControllerActionTest {

    @Test
    fun `every leaf's toString names its type only`() {
        val uuid = ReducerTestFixtures.exerciseA
        val actions = listOf(
            ControllerAction.AdjustDraft(NumericField.WEIGHT, steps = 3),
            ControllerAction.SetReps(997),
            ControllerAction.SetWeight(98_765),
            ControllerAction.CompleteSet,
            ControllerAction.Retry,
            ControllerAction.SelectExercise(uuid),
            ControllerAction.SelectSet(uuid, setPosition = 4),
            ControllerAction.SkipExercise(uuid),
            ControllerAction.ReturnExercise(uuid),
        )

        actions.forEach { action -> assertEquals(typeName(action), action.toString()) }
        assertEquals(LEAVES, actions.map(::typeName).toSet().size, "one of each leaf")
    }

    /** Exhaustive: a new leaf does not compile until it is listed here and above. */
    private fun typeName(action: ControllerAction): String = when (action) {
        is ControllerAction.AdjustDraft -> "AdjustDraft"
        is ControllerAction.SetReps -> "SetReps"
        is ControllerAction.SetWeight -> "SetWeight"
        ControllerAction.CompleteSet -> "CompleteSet"
        ControllerAction.Retry -> "Retry"
        is ControllerAction.SelectExercise -> "SelectExercise"
        is ControllerAction.SelectSet -> "SelectSet"
        is ControllerAction.SkipExercise -> "SkipExercise"
        is ControllerAction.ReturnExercise -> "ReturnExercise"
    }

    private companion object {
        const val LEAVES = 9
    }
}
