// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction

/** SP5: records what a handler asks of the picker; nothing else happens. */
internal class FakeExercisePicker : ExercisePicker {

    var opens: Int = 0
        private set

    val actions: MutableList<ExercisePickerAction> = mutableListOf()

    override fun open() {
        opens += 1
    }

    override operator fun invoke(action: ExercisePickerAction) {
        actions += action
    }
}
