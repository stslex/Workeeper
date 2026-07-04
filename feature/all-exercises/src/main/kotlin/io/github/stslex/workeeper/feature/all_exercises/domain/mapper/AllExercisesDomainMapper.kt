// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseListItem
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain

internal object AllExercisesDomainMapper {

    fun ExerciseDataModel.toDomain(): ExerciseDomain = ExerciseDomain(
        uuid = uuid,
        name = name,
        type = type.toDomain(),
        description = description,
        imagePath = imagePath,
        archived = archived,
        archivedAt = archivedAt,
        timestamp = timestamp,
    )

    fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
        ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
        ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
    }

    fun TagDataModel.toDomain(): TagDomain = TagDomain(uuid = uuid, name = name)

    fun ExerciseRepository.BulkArchiveOutcome.toDomain(): BulkArchiveResult = BulkArchiveResult(
        archivedCount = archivedCount,
        blocked = blocked.map { it.toDomain() },
    )

    private fun ExerciseRepository.BulkArchiveOutcome.BlockedExercise.toDomain():
        BulkArchiveResult.BlockedExerciseDomain = BulkArchiveResult.BlockedExerciseDomain(
        name = name,
        activeTrainings = activeTrainings,
    )

    fun ExerciseListItem.toDomain(): ExerciseListItemDomain = ExerciseListItemDomain(
        exercise = data.toDomain(),
        tags = tags,
        sessionCount = sessionCount,
        linkedTrainingsCount = linkedTrainingsCount,
        lastTrainedAt = lastTrainedAt,
    )
}
