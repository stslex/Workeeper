// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

data class ExerciseAdhocPlanRow(
    @ColumnInfo(name = "uuid") val uuid: Uuid,
    @ColumnInfo(name = "last_adhoc_sets") val lastAdhocSets: String?,
)
