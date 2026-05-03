// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

internal data class PerformedExerciseDomain(
    val uuid: String,
    val sessionUuid: String,
    val exerciseUuid: String,
    val position: Int,
    val skipped: Boolean,
    val exerciseName: String,
)
