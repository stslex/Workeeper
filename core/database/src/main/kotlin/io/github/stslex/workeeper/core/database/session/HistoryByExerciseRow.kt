// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

import androidx.room.ColumnInfo
import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity
import kotlin.uuid.Uuid

/**
 * One row per logged set within a finished session that touched the exercise. The repository
 * groups consecutive rows with the same `session_uuid` into a single history entry — done
 * server-side with paged retrieval to stay friendly to large histories.
 */
data class HistoryByExerciseRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
    @ColumnInfo(name = "weight") val weight: Double?,
    @ColumnInfo(name = "reps") val reps: Int,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "set_type") val setType: SetTypeEntity,
)
