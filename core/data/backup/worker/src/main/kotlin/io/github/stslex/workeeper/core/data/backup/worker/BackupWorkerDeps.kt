// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider

/**
 * The app-scope types [BackupWorker] reads, acquired through a typed holder rather than
 * `appDeps<T>()` — that lives in `core:ui:mvi`, and a data -> ui edge would invert the layers.
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

    /** `null` once the process declared its database unprovable: touch no database, record none. */
    suspend fun awaitBackupWorkLease(): BackupWorkLease?
}
