// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain.model

internal data class SessionDetailDomain(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val exercises: List<PerformedExerciseDetailDomain>,
)

internal data class PerformedExerciseDetailDomain(
    val performedExerciseUuid: String,
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeDomain,
    val position: Int,
    val skipped: Boolean,
    val sets: List<SetDomain>,
)
