// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

data class AddExerciseResult(
    val performedExerciseUuid: String,
    val planSets: List<PlanSetDomain>?,
    /** Echo of the requested `attachToPlan`. See [LiveExerciseDomain.isPlanAttached]. */
    val isPlanAttached: Boolean,
)
