package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import kotlinx.collections.immutable.ImmutableList

@Stable
internal data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val labels: ImmutableList<String>,
    val sets: Int,
    val timestamp: DateProperty,
)