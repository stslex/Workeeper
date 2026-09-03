// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

/**
 * Orchestration seam for the AI-readable JSON snapshot, run after the binary backup on both
 * trigger points. Best-effort: gated on the toggle + `drive.file` grant, and never throws.
 */
interface SnapshotExportRunner {

    /** Fire-and-forget export on an app-scoped coroutine; for the foreground manual path. */
    fun runIfEligible()

    /**
     * Like [runIfEligible] but SUSPENDS until the export completes.
     * GUARD: the worker must use this; a detached launch races process death after `doWork`.
     */
    suspend fun runIfEligibleAwaiting()

    /**
     * Deletes all exported snapshots from the visible folder, serialized against the export
     * paths. Call when the toggle goes OFF and BEFORE sign-out revokes `drive.file`.
     */
    suspend fun clearSnapshots()
}
