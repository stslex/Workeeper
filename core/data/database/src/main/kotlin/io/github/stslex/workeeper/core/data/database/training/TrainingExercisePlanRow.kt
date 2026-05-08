// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.training

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

data class TrainingExercisePlanRow(
    @ColumnInfo(name = "exercise_uuid") val exerciseUuid: Uuid,
    @ColumnInfo(name = "plan_sets") val planSets: String?,
)
