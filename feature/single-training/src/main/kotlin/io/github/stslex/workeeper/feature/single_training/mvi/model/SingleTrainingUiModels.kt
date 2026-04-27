// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import kotlinx.collections.immutable.ImmutableList

@Stable
data class TrainingExerciseItem(
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeUiModel,
    val tags: ImmutableList<String>,
    val position: Int,
    val planSets: ImmutableList<PlanSetUiModel>?,
    val planSummary: String,
)

@Stable
data class HistorySessionItem(
    val sessionUuid: String,
    val finishedAt: Long,
    val trainingName: String,
    val exerciseCount: Int,
)

@Stable
data class PickerExerciseItem(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeUiModel,
    val tags: ImmutableList<String>,
)
