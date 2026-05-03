// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.mapper

import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal fun ExerciseDomain.toUi(
    sessionCount: Int = 0,
    tags: List<String> = emptyList(),
): ExerciseUiModel = ExerciseUiModel(
    uuid = uuid,
    name = name,
    type = type.toUi(),
    tags = if (tags.isEmpty()) persistentListOf() else tags.toImmutableList(),
    sessionCount = sessionCount,
    imagePath = imagePath,
)

internal fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
    ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
    ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
}

internal fun TagDomain.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
