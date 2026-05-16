// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider

/**
 * Constructs a real [BackupWorker] with the supplied mocked dependencies so
 * tests can call `doWork()` directly without instantiating the full Hilt graph.
 * Mirrors the binding that the @HiltWorker code generator produces at runtime.
 */
internal class WorkerTestFactory(
    private val backupStorage: BackupStorage,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val preferences: BackupPreferencesRepository,
    private val autoBackupController: AutoBackupController,
    private val notificationHelper: BackupNotificationHelper,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker = BackupWorker(
        appContext = appContext,
        workerParams = workerParameters,
        backupStorage = backupStorage,
        snapshotProvider = snapshotProvider,
        preferences = preferences,
        autoBackupController = autoBackupController,
        notificationHelper = notificationHelper,
    )
}
