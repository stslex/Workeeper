package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
internal data class TrainingUiModel(
    val uuid: String,
    val name: String,
    val labels: ImmutableList<String>,
    val exercises: List<ExerciseUiModel>,
    val date: DateProperty,
) {

    companion object {

        val INITIAL: TrainingUiModel = TrainingUiModel(
            uuid = "",
            name = "",
            labels = persistentListOf(),
            exercises = persistentListOf(),
            date = DateProperty.new(System.currentTimeMillis())
        )
    }
}
