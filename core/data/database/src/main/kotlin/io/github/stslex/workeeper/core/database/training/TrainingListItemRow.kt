// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.training

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Projection used by `TrainingDao.pagedActiveWithStats` and friends. Joins the trainings
 * table with derived stats so the library tab can render exercise count, last finished
 * session and the current in-progress session in one paged query.
 */
data class TrainingListItemRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
    @ColumnInfo(name = "archived") val archived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "exercise_count") val exerciseCount: Int,
    @ColumnInfo(name = "last_session_at") val lastSessionAt: Long?,
    @ColumnInfo(name = "active_session_uuid") val activeSessionUuid: Uuid?,
    @ColumnInfo(name = "active_session_started_at") val activeSessionStartedAt: Long?,
)
