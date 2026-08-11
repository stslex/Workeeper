// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

data class RecentExerciseDomain(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDomain,
    val lastFinishedAt: Long,
)
