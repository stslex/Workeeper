// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider

/**
 * The `core:data:backup:worker` dep interface for the god-object split (variant A, mechanism A). Names the
 * exact six app-scope types [MetroWorkerFactory] reads — no spine (a WorkManager `WorkerFactory` is not a
 * Store consumer), no dispatchers/qualifiers.
 *
 * ACQUISITION — TYPED POINT-ACQUISITION, not `appDeps<T>()`: this is the DATA layer, and `appDeps<T>()` is
 * homed in `core:ui:mvi` (the UI layer). A `data → ui` dependency would invert the module direction, so the
 * Worker MUST NOT depend on `core:ui:mvi` and therefore CANNOT use `appDeps<T>()`. Instead it hosts its own
 * concrete typed holder ([BackupWorkerDepsHolder]) — the same layer-appropriate pattern `RecoveryActivity`
 * uses. `BaseApplication` implements the holder; `appGraph` (which implements this interface) is handed back
 * typed as `BackupWorkerDeps`.
 *
 * All six types are owned by modules `core:data:backup:worker` ALREADY depends on directly
 * (`core:data:backup:api` for five, `core:data:database` for `databaseSnapshotProvider`) — no new edge, no
 * cycle, and NO reliance on `core:di` (dropped in this commit).
 */
interface BackupWorkerDeps {
    val backupStorage: BackupStorage
    val databaseSnapshotProvider: DatabaseSnapshotProvider
    val backupPreferencesRepository: BackupPreferencesRepository
    val autoBackupController: AutoBackupController
    val backupNotificationHelper: BackupNotificationHelper
    val snapshotExportRunner: SnapshotExportRunner
}

/**
 * Held-instance seam for [BackupWorkerDeps]: the process `Application` exposes the app-scope graph typed as
 * [BackupWorkerDeps]. Returns the concrete interface (NOT `Any`) — no reified generic, no unchecked cast.
 * This is the accepted layer-specific acquisition for the data layer, which cannot reach the UI-homed
 * `appDeps<T>()`.
 *
 * [MetroWorkerFactory] reads it via `(appContext.applicationContext as BackupWorkerDepsHolder)
 * .backupWorkerDeps()` — the cast is safe by construction because `BaseApplication : BackupWorkerDepsHolder`
 * (compile-visible).
 */
interface BackupWorkerDepsHolder {

    fun backupWorkerDeps(): BackupWorkerDeps

    /**
     * Atomic worker admission (Phase 5 R2, closed-admission quiesce): waits while a runtime
     * transition holds admission closed (bounded by the transition window), then returns the
     * CURRENT generation's deps together with the lease the quiesce drain awaits. May block —
     * called from WorkManager's synchronous `createWorker` on its serial task-executor thread,
     * where parking for the bounded window binds the worker to exactly one generation instead
     * of tearing it across two. Throws when the runtime is Fatal (no generation may admit work).
     */
    fun acquireBackupWorkLease(): BackupWorkLease
}
