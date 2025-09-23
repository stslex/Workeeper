package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val dateProperty: PropertyHolder.DateProperty
)

fun ExerciseDataModel.toUi() = ExerciseUiModel(
    uuid = uuid,
    name = name,
    dateProperty = PropertyHolder.DateProperty(
        initialValue = timestamp,
    ),
)