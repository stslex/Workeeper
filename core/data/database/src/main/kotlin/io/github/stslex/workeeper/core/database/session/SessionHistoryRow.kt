// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

data class SessionHistoryRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "performed_exercise_uuid") val performedExerciseUuid: Uuid,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
)
