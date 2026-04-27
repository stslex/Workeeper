// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.training

import io.github.stslex.workeeper.core.database.training.TrainingListItemRow

/**
 * Library-tab projection of a training plus its derived stats. `lastSessionAt` and the
 * `activeSession*` fields are pre-computed by the DAO so the UI does not run additional
 * queries per row.
 */
data class TrainingListItem(
    val data: TrainingDataModel,
    val exerciseCount: Int,
    val lastSessionAt: Long?,
    val isActive: Boolean,
    val activeSessionUuid: String?,
    val activeSessionStartedAt: Long?,
)

internal fun TrainingListItemRow.toData(
    labels: List<String> = emptyList(),
): TrainingListItem = TrainingListItem(
    data = TrainingDataModel(
        uuid = uuid.toString(),
        name = name,
        description = description,
        isAdhoc = isAdhoc,
        archived = archived,
        archivedAt = archivedAt,
        timestamp = createdAt,
        labels = labels,
    ),
    exerciseCount = exerciseCount,
    lastSessionAt = lastSessionAt,
    isActive = activeSessionUuid != null,
    activeSessionUuid = activeSessionUuid?.toString(),
    activeSessionStartedAt = activeSessionStartedAt,
)
