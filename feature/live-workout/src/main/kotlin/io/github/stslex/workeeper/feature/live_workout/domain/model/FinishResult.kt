// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

internal data class FinishResult(
    val durationMillis: Long,
    val doneCount: Int,
    val totalCount: Int,
    val skippedCount: Int,
    val setsLogged: Int,
)
