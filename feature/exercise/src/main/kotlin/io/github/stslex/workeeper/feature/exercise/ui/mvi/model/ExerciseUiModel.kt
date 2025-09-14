package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val sets: ImmutableList<SetsUiModel>,
    val timestamp: Long,
)

internal fun ExerciseDataModel.toUi() = ExerciseUiModel(
    uuid = uuid,
    name = name,
    sets = sets.map { it.toUi() }.toImmutableList(),
    timestamp = timestamp,
)