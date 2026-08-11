// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

/** One exercise's plan, addressed by uuid — the unit [saveTraining]'s plan list carries. */
data class ExercisePlanDomain(
    val exerciseUuid: String,
    val planSets: List<PlanSetDomain>?,
)
