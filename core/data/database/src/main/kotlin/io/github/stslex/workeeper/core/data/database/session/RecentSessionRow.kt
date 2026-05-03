// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Projection for the Home recent-sessions list. Joins finished sessions with their training
 * row plus aggregated counts of performed exercises and logged sets so each row renders
 * without an extra round trip per session.
 */
data class RecentSessionRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "exercise_count") val exerciseCount: Int,
    @ColumnInfo(name = "set_count") val setCount: Int,
)
