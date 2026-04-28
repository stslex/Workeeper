// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.mapper

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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

internal fun TagDataModel.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
