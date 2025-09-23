package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
internal data class TrainingUiModel(
    val uuid: String,
    val name: String,
    val labels: ImmutableList<String>,
    val exercises: ImmutableList<ExerciseUiModel>,
    val date: PropertyHolder.DateProperty,
) {

    companion object {

        val INITIAL: TrainingUiModel = TrainingUiModel(
            uuid = "",
            name = "",
            labels = persistentListOf(),
            exercises = persistentListOf(),
            date = PropertyHolder.DateProperty().update(System.currentTimeMillis())
        )
    }
}
