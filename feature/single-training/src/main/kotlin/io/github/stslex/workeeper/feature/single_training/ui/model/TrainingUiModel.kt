package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
internal data class TrainingUiModel(
    val uuid: String,
    val name: PropertyHolder.StringProperty,
    val isMenuOpen: Boolean,
    val menuItems: ImmutableSet<MenuItem<TrainingUiModel>>,
    val labels: ImmutableList<String>,
    val exercises: ImmutableList<ExerciseUiModel>,
    val date: PropertyHolder.DateProperty,
) {

    companion object {

        val INITIAL: TrainingUiModel = TrainingUiModel(
            uuid = "",
            name = PropertyHolder.StringProperty.empty(),
            labels = persistentListOf(),
            exercises = persistentListOf(),
            date = PropertyHolder.DateProperty.now(),
            isMenuOpen = false,
            menuItems = persistentSetOf(),
        )
    }
}
