// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TrainingListItemDomain

internal fun TrainingListItem.toDomain(): TrainingListItemDomain = TrainingListItemDomain(
    uuid = data.uuid,
    name = data.name,
    tags = data.labels,
    exerciseCount = exerciseCount,
    lastSessionAt = lastSessionAt,
    isActive = isActive,
    activeSessionUuid = activeSessionUuid,
    activeSessionStartedAt = activeSessionStartedAt,
)

internal fun TagDataModel.toDomain(): TagDomain = TagDomain(uuid = uuid, name = name)

internal fun TrainingRepository.BulkArchiveOutcome.toDomain(): BulkArchiveResult = BulkArchiveResult(
    archivedCount = archivedCount,
    blockedNames = blockedNames,
)
