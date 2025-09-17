package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import kotlinx.collections.immutable.ImmutableList

@Stable
internal data class TrainingUiModel(
    val uuid: String,
    val name: String,
    val labels: ImmutableList<String>,
    val exerciseUuids: ImmutableList<String>,
    val date: DateProperty,
)