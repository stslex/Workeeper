// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

data class LiveExerciseDomain(
    val performed: PerformedExerciseDomain,
    val exerciseType: ExerciseTypeDomain,
    val planSets: List<PlanSetDomain>?,
    val performedSets: List<SetDomain>,
)
