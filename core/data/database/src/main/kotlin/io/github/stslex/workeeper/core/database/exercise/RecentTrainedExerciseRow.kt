// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.exercise

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Projection for the v2.2 chart exercise picker. Each row is an active (non-archived)
 * exercise the user has trained at least once in a finished session, ordered by the most
 * recent finish.
 */
data class RecentTrainedExerciseRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: ExerciseTypeEntity,
    @ColumnInfo(name = "last_finished_at") val lastFinishedAt: Long,
)
