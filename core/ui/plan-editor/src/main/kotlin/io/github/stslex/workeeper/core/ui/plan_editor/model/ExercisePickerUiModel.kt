// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import androidx.compose.runtime.Immutable

/**
 * Picker-local UI model for the v2.3 "add exercise to active session" bottom sheet.
 * Lives in `core/ui/plan-editor` so the kit component does not depend on any feature's
 * domain types — feature handlers map their `*DataModel` rows into this shape.
 */
@Immutable
data class ExercisePickerUiModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeUiModel,
)
