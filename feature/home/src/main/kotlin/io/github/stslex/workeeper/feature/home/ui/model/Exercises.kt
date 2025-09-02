package io.github.stslex.workeeper.feature.home.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Int,
    val timestamp: Long,
)

fun ExerciseDataModel.toUi() = ExerciseUiModel(
    uuid = uuid,
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    timestamp = timestamp,
)