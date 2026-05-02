// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import androidx.compose.runtime.Stable

/**
 * Sub-action surface emitted by the v2.3 `ExercisePickerBottomSheet`. Wrapped in the
 * consuming feature's `Action.Click.PickerAction` to keep the feature's top-level Click
 * surface flat while still routing through a dedicated picker sub-handler.
 */
@Stable
sealed interface ExercisePickerAction {
    data class OnQueryChange(val query: String) : ExercisePickerAction
    data class OnExerciseSelect(val exerciseUuid: String) : ExercisePickerAction
    data class OnCreateNewExercise(val name: String) : ExercisePickerAction
    data object OnDismiss : ExercisePickerAction
}
