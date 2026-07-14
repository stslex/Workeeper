// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.notification

/**
 * Owns the lifecycle of the persistent "Auto-backup paused" notification surfaced by `BackupWorker`
 * on `BackupError.AuthRevoked`.
 *
 * Extracted to `core:data:backup:api` in App-Scope Collapse Step 6 (worker de-cycle): the impl
 * (`core:data:backup:worker`'s `BackupNotificationHelperImpl`) is Metro-owned via
 * `@ContributesBinding(AppScope)` and exposed on the app graph as `backupNotificationHelper`. The
 * CONTRACT lives here — in the api module both `core:di` and `core:data:backup:worker` already see —
 * so `AppGraphContract` (in `core:di`) can name it WITHOUT `core:di` depending on the worker module
 * (that edge was the P-WORKER dependency cycle: `core:di → worker → core:di`). The impl keeps the
 * Android `Context`/`R`/notification logic; this interface is the neutral seam.
 */
interface BackupNotificationHelper {

    /** Show (or refresh) the persistent low-importance "Auto-backup paused" notification. */
    fun showAuthPaused()

    /** Cancel the "Auto-backup paused" notification. */
    fun cancelAuthPaused()
}
