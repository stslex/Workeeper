// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel

@Stable
internal data class ExercisePickerItemUiModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeUiModel,
)
