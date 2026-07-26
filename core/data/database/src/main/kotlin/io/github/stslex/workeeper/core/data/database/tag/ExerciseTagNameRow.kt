// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Batch projection: one (exercise, tag-name) pair. Returned by
 * [ExerciseTagDao.getAllExerciseTagNames] so the snapshot exporter resolves
 * denormalized tag names for every exercise in a single full-table query, then groups by
 * [exerciseUuid] in memory. Mirrors the `TrainingExercisePlanRow` batch pattern.
 */
data class ExerciseTagNameRow(
    @ColumnInfo(name = "exercise_uuid") val exerciseUuid: Uuid,
    @ColumnInfo(name = "name") val name: String,
)
