// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.NumericField

/**
 * What the watch asks of its runtime. The MVI registry does not cover these (F21), so every leaf's
 * `toString()` names its type only: no value, name or selection (wear-phase-2-exercise-list.md §7.4).
 */
internal sealed interface ControllerAction {
    data class AdjustDraft(val field: NumericField, val steps: Int) : ControllerAction {
        override fun toString(): String = "AdjustDraft"
    }

    data class SetReps(val value: Int) : ControllerAction {
        override fun toString(): String = "SetReps"
    }

    data class SetWeight(val value: Int?) : ControllerAction {
        override fun toString(): String = "SetWeight"
    }

    data object CompleteSet : ControllerAction
    data object Retry : ControllerAction

    /** Opening an exercise from the list: its first incomplete set (§7.2). */
    data class SelectExercise(val performedExerciseUuid: CanonicalUuid) : ControllerAction {
        override fun toString(): String = "SelectExercise"
    }

    /** Picking an `Open` or `Blocked` set on Sets (§7.2). */
    data class SelectSet(val performedExerciseUuid: CanonicalUuid, val setPosition: Int) : ControllerAction {
        override fun toString(): String = "SelectSet"
    }

    /** `set_exercise_skipped` with `skipped` true (§7.4). */
    data class SkipExercise(val performedExerciseUuid: CanonicalUuid) : ControllerAction {
        override fun toString(): String = "SkipExercise"
    }

    /** `set_exercise_skipped` with `skipped` false (§7.4). */
    data class ReturnExercise(val performedExerciseUuid: CanonicalUuid) : ControllerAction {
        override fun toString(): String = "ReturnExercise"
    }
}
