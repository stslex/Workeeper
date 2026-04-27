// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

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
