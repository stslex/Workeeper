// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType

/**
 * Heaviest set logged for an exercise across finished sessions. `weight` is null for
 * weightless exercises (the metric collapses to reps + earliest-finish-wins).
 */
data class PersonalRecordDataModel(
    val sessionUuid: String,
    val performedExerciseUuid: String,
    val setUuid: String,
    val weight: Double?,
    val reps: Int,
    val type: SetsDataType,
    val finishedAt: Long,
)
