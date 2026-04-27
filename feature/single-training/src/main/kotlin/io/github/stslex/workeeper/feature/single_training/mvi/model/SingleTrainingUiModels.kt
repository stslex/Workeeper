// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import kotlinx.collections.immutable.ImmutableList

@Stable
data class TrainingExerciseItem(
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeDataModel,
    val tags: ImmutableList<String>,
    val position: Int,
    val planSets: ImmutableList<PlanSetDataModel>?,
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
    val type: ExerciseTypeDataModel,
    val tags: ImmutableList<String>,
)
