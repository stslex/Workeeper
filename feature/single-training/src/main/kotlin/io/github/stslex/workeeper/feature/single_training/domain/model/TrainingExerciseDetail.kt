// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

internal data class TrainingExerciseDetail(
    val exercise: ExerciseDomain,
    val position: Int,
    val planSets: List<PlanSetDomain>?,
    val labels: List<String>,
)
