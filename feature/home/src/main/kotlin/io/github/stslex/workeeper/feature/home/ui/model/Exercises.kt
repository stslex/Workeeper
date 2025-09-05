package io.github.stslex.workeeper.feature.home.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.utils.DateTimeUtil
import io.github.stslex.workeeper.core.ui.navigation.Screen

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val dateProperty: DateProperty
)

fun ExerciseDataModel.toUi() = ExerciseUiModel(
    uuid = uuid,
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    dateProperty = DateProperty(
        timestamp = timestamp,
        converted = DateTimeUtil.formatMillis(timestamp)
    ),
)

fun ExerciseUiModel.toNavData() = Screen.Exercise.Data(
    uuid = uuid,
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    timestamp = dateProperty.timestamp,
    converted = dateProperty.converted
)