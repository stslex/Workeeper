package io.github.stslex.workeeper.feature.home.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.navigation.Config

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
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

fun ExerciseUiModel.toNavData() = Config.Exercise.Data.Edit(
    uuid = uuid,
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    timestamp = timestamp
)