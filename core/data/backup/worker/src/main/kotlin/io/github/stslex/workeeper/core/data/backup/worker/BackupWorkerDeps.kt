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

/** Data-layer seam for first-operation worker admission. */
interface BackupWorkerDepsHolder {

    suspend fun awaitBackupWorkLease(): BackupWorkLease
}
