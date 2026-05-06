// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.exercise.model

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseListItemRow

/**
 * Library-tab projection of an exercise plus its derived stats and tag labels. The DAO
 * row carries the entity + three correlated subquery results; the repo joins denormalized
 * tag names per row so the UI does not run additional queries. (v2.4 E6.)
 */
data class ExerciseListItem(
    val data: ExerciseDataModel,
    val tags: List<String>,
    val sessionCount: Int,
    val linkedTrainingsCount: Int,
    val lastTrainedAt: Long?,
)

internal fun ExerciseListItemRow.toData(
    tags: List<String> = emptyList(),
): ExerciseListItem = ExerciseListItem(
    data = exercise.toData(),
    tags = tags,
    sessionCount = sessionCount,
    linkedTrainingsCount = linkedTrainingsCount,
    lastTrainedAt = lastTrainedAt,
)
