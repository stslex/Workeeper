// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import androidx.room3.ColumnInfo
import kotlin.uuid.Uuid

/** Slim projection for the global "is anything in progress?" query. */
data class ActiveSessionRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

/** Projection for the Home active-session banner; `done_count` is heuristic (any logged set). */
data class ActiveSessionWithStatsRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "done_count") val doneCount: Int,
)
