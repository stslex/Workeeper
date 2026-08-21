// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.ColumnInfo
import kotlin.uuid.Uuid

/**
 * Batch projection: one (training, tag-name) pair. Returned by
 * [TrainingTagDao.getAllTrainingTagNames] so the snapshot exporter resolves
 * denormalized tag names for every training in a single full-table query, then groups by
 * [trainingUuid] in memory. Mirrors the `TrainingExercisePlanRow` batch pattern.
 */
data class TrainingTagNameRow(
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "name") val name: String,
)
