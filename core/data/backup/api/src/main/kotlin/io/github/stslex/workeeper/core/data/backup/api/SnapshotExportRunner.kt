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
     * so it never blocks or delays the binary backup (D2) — the manual UI and the worker
     * `Result` no longer wait on visible-Drive latency. No-op unless the AI-export toggle is on
     * AND `drive.file` is granted. Best-effort: the detached work never throws (unexpected errors
     * → Crashlytics non-fatal, transient ones log-only), and if the process dies before it
     * finishes the next backup re-exports.
     */
    fun runIfEligible()
}
