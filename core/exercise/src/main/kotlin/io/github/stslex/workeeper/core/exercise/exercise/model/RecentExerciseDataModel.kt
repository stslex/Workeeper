// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.exercise.model

/**
 * One picker entry for the v2.2 chart screen — an active exercise the user has trained at
 * least once. [lastFinishedAt] is the timestamp of the most recent finished session that
 * touched the exercise; the list is ordered descending by this value.
 */
data class RecentExerciseDataModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDataModel,
    val lastFinishedAt: Long,
)
