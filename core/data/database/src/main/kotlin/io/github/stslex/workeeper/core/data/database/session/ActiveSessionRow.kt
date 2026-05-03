// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Slim projection for the global "is anything in progress?" query. Avoids loading the
 * full `SessionEntity` when the UI only needs the training reference.
 */
data class ActiveSessionRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

/**
 * Projection that powers the Home active-session banner. Carries the training name +
 * `is_adhoc` flag plus done/total exercise counts so the banner can render without an
 * extra round trip per session. `done_count` is heuristic (any logged set ⇒ done).
 */
data class ActiveSessionWithStatsRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "done_count") val doneCount: Int,
)
