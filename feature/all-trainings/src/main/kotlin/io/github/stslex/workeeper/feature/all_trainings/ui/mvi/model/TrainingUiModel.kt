package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import kotlinx.collections.immutable.ImmutableList

@Stable
internal data class TrainingUiModel(
    val uuid: String,
    val name: String,
    val labels: ImmutableList<String>,
    val exerciseUuids: ImmutableList<String>,
    val date: PropertyHolder.DateProperty,
)
