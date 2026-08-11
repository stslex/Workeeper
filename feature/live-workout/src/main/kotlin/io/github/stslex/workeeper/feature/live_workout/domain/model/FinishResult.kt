// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

data class FinishResult(
    val durationMillis: Long,
    val doneCount: Int,
    val totalCount: Int,
    val skippedCount: Int,
    val setsLogged: Int,
    /**
     * Persisted rows with `reps <= 0` removed by the finish path (§6.1).
     *
     * Expected to be `0` on every session produced by this app: measured on this tree, no
     * production writer can persist a zero-rep set. `SetRepository.upsert` is reachable only
     * through `ClickHandler.processSetMarkDone`, which rejects `reps <= 0`; `SetRepository.update`
     * only through past-session's `InputHandler`, which requires `parsed > 0`; and
     * `SetRepository.insert` has no production caller at all. A non-zero value here therefore
     * means legacy or imported data, or a new writer that broke the invariant.
     *
     * The count the user sees in `FinishConfirmDialog` is a different number — it comes from
     * `State.unfilledSetCount`, the empty rows visible on screen, which never reached the
     * database in the first place.
     */
    val discardedUnfilledSets: Int = 0,
)
