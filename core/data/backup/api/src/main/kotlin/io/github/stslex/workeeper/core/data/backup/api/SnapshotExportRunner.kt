// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

/**
 * Orchestration seam for the AI-readable JSON snapshot. Invoked after the binary backup on
 * both trigger points (auto worker + manual). Fully best-effort and decoupled: it gates on
 * the user toggle AND the `drive.file` grant, and its entire body is swallowed so it can
 * never block, delay, or fail the binary backup.
 */
interface SnapshotExportRunner {

    /**
     * Fire-and-forget: launches the export on an app-scoped coroutine and returns immediately,
     * so it never blocks or delays the binary backup (D2). Correct for the FOREGROUND manual path
     * (`BackupInteractorImpl.createBackup`), whose UI must not wait on visible-Drive latency.
     * No-op unless the AI-export toggle is on AND `drive.file` is granted. Best-effort: the
     * detached work never throws (unexpected errors → Crashlytics non-fatal, transient ones
     * log-only), and if the process dies before it finishes the next backup re-exports.
     */
    fun runIfEligible()

    /**
     * Like [runIfEligible] but SUSPENDS until the export completes. For the auto-backup WORKER,
     * which already holds a wakelock/execution window: a detached launch would forfeit that window
     * (`doWork()` returns, the process becomes reclaim-eligible) and the snapshot would race
     * process death on every periodic run. Awaiting keeps the window alive until the upload
     * finishes. MUST be called only AFTER the binary-backup result is computed, so D2 holds — the
     * binary upload is already done and only the worker's `Result` reporting is held slightly
     * longer. Still fully best-effort: the body is swallowed internally and never throws. No-op
     * unless the toggle is on AND `drive.file` is granted.
     */
    suspend fun runIfEligibleAwaiting()

    /**
     * Deletes all previously-exported snapshots from the visible folder, serialized against
     * [runIfEligible] / [runIfEligibleAwaiting] through the same in-process lock so a delete can
     * never race a concurrent upload. SUSPENDS until done. Call when the user withdraws consent
     * (AI-export toggle OFF) and BEFORE sign-out revokes `drive.file` — once the grant is gone the
     * app can no longer see its visible files to remove them. Fully best-effort: gated on the
     * `drive.file` grant (no-op without it) and the body is swallowed so it never throws. Unlike
     * the export paths this does NOT gate on the toggle — it is invoked precisely when the toggle
     * is going off.
     */
    suspend fun clearSnapshots()
}
