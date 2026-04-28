// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import kotlinx.collections.immutable.ImmutableList

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDataModel,
    val tags: ImmutableList<String>,
    val sessionCount: Int,
)
