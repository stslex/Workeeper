// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

internal data class PersonalRecordDomain(
    val sessionUuid: String,
    val performedExerciseUuid: String,
    val setUuid: String,
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDomain,
    val finishedAt: Long,
)
