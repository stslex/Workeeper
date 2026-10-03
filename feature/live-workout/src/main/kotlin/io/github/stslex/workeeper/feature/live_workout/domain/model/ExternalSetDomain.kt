// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

/**
 * One set a writer outside this screen committed (the paired watch, through the phone bridge),
 * with the values exactly as written (wear-live-sync.md §6.4).
 */
data class ExternalSetDomain(
    val performedExerciseUuid: String,
    val position: Int,
    val set: PlanSetDomain,
)
