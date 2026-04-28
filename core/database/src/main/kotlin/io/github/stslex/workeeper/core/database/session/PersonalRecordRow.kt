// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

import androidx.room.ColumnInfo
import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity
import kotlin.uuid.Uuid

/**
 * Heaviest set per exercise across finished sessions. Drives the v2.1 PR detection /
 * Exercise detail PR block. The aggregation runs at read time; no caching layer in v2.0.
 */
data class PersonalRecordRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "performed_exercise_uuid") val performedExerciseUuid: Uuid,
    @ColumnInfo(name = "set_uuid") val setUuid: Uuid,
    @ColumnInfo(name = "weight") val weight: Double?,
    @ColumnInfo(name = "reps") val reps: Int,
    @ColumnInfo(name = "type") val type: SetTypeEntity,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
)
