// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Stable
data class ExerciseUiModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDataModel,
    val tags: ImmutableList<String>,
    val sessionCount: Int,
)

internal fun ExerciseDataModel.toUi(
    sessionCount: Int = 0,
    tags: List<String> = emptyList(),
): ExerciseUiModel = ExerciseUiModel(
    uuid = uuid,
    name = name,
    type = type,
    tags = if (tags.isEmpty()) persistentListOf() else tags.toImmutableList(),
    sessionCount = sessionCount,
)
