// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.notification

/**
 * Owns the lifecycle of the persistent "Auto-backup paused" notification surfaced by `BackupWorker`
 * on `BackupError.AuthRevoked`.
 *
 * Extracted to `core:data:backup:api` in App-Scope Collapse Step 6 (worker de-cycle): the impl
 * (`core:data:backup:worker`'s `BackupNotificationHelperImpl`) is Metro-owned via
 * `@ContributesBinding(AppScope)` and exposed on the app graph as `backupNotificationHelper`. The
 * CONTRACT lives here — in the api module that both the app graph and `core:data:backup:worker`
 * already see — so the app graph can name it WITHOUT depending on the worker module (that edge was
 * the P-WORKER dependency cycle: `app-graph → worker → app-graph`). The impl keeps the
 * Android `Context`/`R`/notification logic; this interface is the neutral seam.
 */
interface BackupNotificationHelper {

    /** Show (or refresh) the persistent low-importance "Auto-backup paused" notification. */
    fun showAuthPaused()

    /** Cancel the "Auto-backup paused" notification. */
    fun cancelAuthPaused()
}
