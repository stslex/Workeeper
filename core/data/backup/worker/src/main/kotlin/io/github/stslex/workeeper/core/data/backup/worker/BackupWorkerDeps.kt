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
 * exact six app-scope types [BackupWorker] reads — no spine (a WorkManager worker is not a
 * Store consumer), no dispatchers/qualifiers.
 *
 * ACQUISITION — TYPED POINT-ACQUISITION, not `appDeps<T>()`: this is the DATA layer, and `appDeps<T>()` is
 * homed in `core:ui:mvi` (the UI layer). A `data → ui` dependency would invert the module direction, so the
 * Worker MUST NOT depend on `core:ui:mvi` and therefore CANNOT use `appDeps<T>()`. Instead it hosts its own
 * concrete typed holder ([BackupWorkerDepsHolder]) — the same layer-appropriate pattern `RecoveryActivity`
 * uses. `BaseApplication` implements the holder; the runtime's generation graph (which implements this
 * interface) is handed back typed as [BackupWorkerDeps] INSIDE an admission lease.
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
 * Held-instance seam for worker admission: the process `Application` exposes the runtime's
 * admission gate typed for the data layer. This is the accepted layer-specific acquisition for
 * the data layer, which cannot reach the UI-homed `appDeps<T>()`.
 *
 * **First-operation admission (Phase 5 R2, spec §8.4).** [BackupWorker] calls
 * [awaitBackupWorkLease] as the FIRST operation inside `doWork` — never at construction. The
 * factory therefore captures NO generation dependencies, and a worker WorkManager constructed
 * but never started holds nothing a transition would have to wait for. The acquisition
 * suspends while a runtime transition holds admission closed (bounded by the transition
 * window) and then returns the CURRENT generation's deps together with the lease the
 * transition's quiesce awaits — deps and lease bound atomically, so the run is coherently
 * owned by exactly one generation. Throws when the runtime is Fatal (no generation may admit
 * work).
 */
interface BackupWorkerDepsHolder {

    suspend fun awaitBackupWorkLease(): BackupWorkLease
}
