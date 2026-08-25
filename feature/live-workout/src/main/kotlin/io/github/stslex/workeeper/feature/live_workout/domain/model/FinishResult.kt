// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

data class FinishResult(
    val durationMillis: Long,
    val doneCount: Int,
    val totalCount: Int,
    val skippedCount: Int,
    val setsLogged: Int,
    /**
     * Persisted rows with `reps <= 0` removed by the finish path (§6.1); expected to be 0, since
     * no production writer can persist one. See the v3 redesign spec.
     */
    val discardedUnfilledSets: Int = 0,
)
