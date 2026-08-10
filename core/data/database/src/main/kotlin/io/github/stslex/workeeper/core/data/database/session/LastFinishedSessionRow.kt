// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import androidx.room3.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Projection for the start card's «Дни без тренировки» readout (home-start-card.md §3.2):
 * the single most recent finished session with the training name that anchors the number.
 */
data class LastFinishedSessionRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
)
