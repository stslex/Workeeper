// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Per-session volume aggregate (Σ weight × reps) limited to weighted exercises. Drives the
 * v2.3 Achievement-block volume fallback and Stats dashboard. Weightless exercises are
 * excluded by the query (mixing kg with raw reps is meaningless).
 */
data class BestSessionVolumeRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "volume") val volume: Double,
)
