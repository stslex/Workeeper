package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.utils.DateTimeUtil

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val dateProperty: DateProperty
)

fun ExerciseDataModel.toUi() = ExerciseUiModel(
    uuid = uuid,
    name = name,
    dateProperty = DateProperty(
        timestamp = timestamp,
        converted = DateTimeUtil.formatMillis(timestamp)
    ),
)