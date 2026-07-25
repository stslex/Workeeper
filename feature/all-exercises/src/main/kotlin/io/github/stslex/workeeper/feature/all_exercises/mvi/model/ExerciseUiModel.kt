// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeUiModel,
    val tags: ImmutableList<String>,
    val sessionCount: Int,
    val linkedTrainingsCount: Int,
    val lastTrainedAt: Long?,
    val footerLabel: String,
    val imagePath: String?,
)
