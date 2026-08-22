// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import java.util.concurrent.atomic.AtomicInteger

/**
 * Constructs a real [BackupWorker] with the supplied mocked dependencies so tests can call
 * `doWork()` directly without a runtime host. Mirrors [MetroWorkerFactory]'s lease admission with
 * a [RecordingBackupWorkLease] — tests read [lease] to assert the release-in-finally contract.
 */
internal class WorkerTestFactory(
    backupStorage: BackupStorage,
    snapshotProvider: DatabaseSnapshotProvider,
    preferences: BackupPreferencesRepository,
    autoBackupController: AutoBackupController,
    notificationHelper: BackupNotificationHelper,
    snapshotExportRunner: SnapshotExportRunner,
) : WorkerFactory() {

    val lease = RecordingBackupWorkLease(
        object : BackupWorkerDeps {
            override val backupStorage: BackupStorage = backupStorage
            override val databaseSnapshotProvider: DatabaseSnapshotProvider = snapshotProvider
            override val backupPreferencesRepository: BackupPreferencesRepository = preferences
            override val autoBackupController: AutoBackupController = autoBackupController
            override val backupNotificationHelper: BackupNotificationHelper = notificationHelper
            override val snapshotExportRunner: SnapshotExportRunner = snapshotExportRunner
        },
    )

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker = BackupWorker(
        appContext = appContext,
        workerParams = workerParameters,
        workLease = lease,
    )
}

/** Test double for [BackupWorkLease] counting [release] calls. */
internal class RecordingBackupWorkLease(
    override val deps: BackupWorkerDeps,
) : BackupWorkLease {

    val releaseCount = AtomicInteger(0)

    override fun release() {
        releaseCount.incrementAndGet()
    }
}
