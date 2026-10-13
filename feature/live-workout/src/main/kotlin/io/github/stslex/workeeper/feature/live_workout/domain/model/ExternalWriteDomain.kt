// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

/**
 * One write a writer outside this screen committed (the paired watch, through the phone bridge),
 * exactly as written (wear-live-sync.md §6.4, wear-phase-2-exercise-list.md §6.5).
 */
sealed interface ExternalWriteDomain {
    val performedExerciseUuid: String
}

/** One set, with the values exactly as written. */
data class ExternalSetDomain(
    override val performedExerciseUuid: String,
    val position: Int,
    val set: PlanSetDomain,
) : ExternalWriteDomain

/** An exercise skipped ([skipped] true) or returned to the session (false); the flag only. */
data class ExternalSkipDomain(
    override val performedExerciseUuid: String,
    val skipped: Boolean,
) : ExternalWriteDomain
