// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

data class LiveExerciseDomain(
    val performed: PerformedExerciseDomain,
    val exerciseType: ExerciseTypeDomain,
    val planSets: List<PlanSetDomain>?,
    val performedSets: List<SetDomain>,
    /**
     * Whether a `training_exercise_table` row exists for the (training, exercise) pair; `false`
     * marks a one-off. Not `is_adhoc` — see the v3 redesign spec §6.2.
     */
    val isPlanAttached: Boolean,
    /** Template description; gates the card's info button and fills the `sh-desc` sheet. */
    val description: String? = null,
)
