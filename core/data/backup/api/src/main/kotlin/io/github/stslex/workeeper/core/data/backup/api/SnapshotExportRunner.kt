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
     * No-op unless the AI-export toggle is on AND `drive.file` is granted. When eligible,
     * builds the JSON snapshot and uploads it. Never throws: unexpected errors are recorded
     * as Crashlytics non-fatals, transient ones are logged only.
     */
    suspend fun runIfEligible()
}
