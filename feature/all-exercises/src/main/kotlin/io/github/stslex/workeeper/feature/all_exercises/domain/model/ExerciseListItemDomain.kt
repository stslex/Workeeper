// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain.model

/**
 * Library-tab projection of an exercise plus its derived stats and tag labels. Powers
 * the paged list footer "N sessions · in M trainings · last Xd ago". (v2.4 E6.)
 */
data class ExerciseListItemDomain(
    val exercise: ExerciseDomain,
    val tags: List<String>,
    val sessionCount: Int,
    val linkedTrainingsCount: Int,
    val lastTrainedAt: Long?,
)
