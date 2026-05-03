// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain

internal fun ExerciseDataModel.toDomain(): ExerciseDomain = ExerciseDomain(
    uuid = uuid,
    name = name,
    type = type.toDomain(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    archivedAt = archivedAt,
    timestamp = timestamp,
)

internal fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun TagDataModel.toDomain(): TagDomain = TagDomain(uuid = uuid, name = name)

internal fun ExerciseRepository.BulkArchiveOutcome.toDomain(): BulkArchiveResult = BulkArchiveResult(
    archivedCount = archivedCount,
    blockedNames = blockedNames,
)
