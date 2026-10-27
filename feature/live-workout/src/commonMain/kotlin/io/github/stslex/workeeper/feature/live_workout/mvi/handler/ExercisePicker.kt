// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction

/**
 * SP5, on Phase 7.13 D2-A's template: what [ClickHandler] and [DialogClickHandler] need from
 * [ExercisePickerHandler], bound in `LiveWorkoutGraph`, so their tests record the calls on a fake.
 */
interface ExercisePicker {

    /** Opens the picker with the library list, minus exercises already in the session. */
    fun open()

    /** Routes one picker sheet action. */
    operator fun invoke(action: ExercisePickerAction)
}
