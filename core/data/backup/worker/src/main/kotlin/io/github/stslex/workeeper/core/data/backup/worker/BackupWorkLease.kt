// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

/**
 * One admitted unit of DB-bound background work (Phase 5 R2, closed admission — spec §8.4
 * Quiescing). Acquired ATOMICALLY with [deps] through
 * [BackupWorkerDepsHolder.awaitBackupWorkLease] as the FIRST operation inside the worker's
 * `doWork` — never at construction — so a run is coherently bound to exactly one runtime
 * generation, no run can capture an outgoing generation's dependencies after a transition
 * closed admission, and a worker constructed but never started holds nothing (no leaked lease).
 *
 * [release] MUST be called exactly once, when the worker's run ends (success, failure, or
 * cancellation — a `finally` around the admitted body); it is idempotent. An unreleased lease
 * blocks replacement transactions until their bounded drain aborts them — loud, never
 * corrupting.
 */
interface BackupWorkLease {

    /** The admitted generation's worker dependencies — read them only through this lease. */
    val deps: BackupWorkerDeps

    fun release()
}
