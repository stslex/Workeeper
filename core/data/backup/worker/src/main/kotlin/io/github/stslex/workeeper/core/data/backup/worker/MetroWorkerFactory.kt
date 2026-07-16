// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.di.appGraphContract

/**
 * Metro-side WorkManager [WorkerFactory].
 *
 * Constructs [BackupWorker] by reading the worker's six app-scoped deps from the Metro app graph via
 * `appContext.appGraphContract()` and calling the constructor directly.
 *
 * Class-name match: we compare `workerClassName` against [BackupWorker]'s fully-qualified name. Any
 * other worker → `null`, so WorkManager's inherited `createWorkerWithDefaultFallback` constructs it via
 * the default reflection factory (verified against work-runtime 2.10.0 `WorkerFactory.kt`). No
 * `DelegatingWorkerFactory` needed — `BackupWorker` is the only worker in the app.
 */
class MetroWorkerFactory(
    private val appContext: Context,
) : WorkerFactory() {

    // Read once, off the application context — the same singletons the app graph holds.
    private val graph by lazy { appContext.applicationContext.appGraphContract() }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != BackupWorker::class.java.name) {
            // Not our worker — return null so WorkManager falls through to the default factory.
            return null
        }
        return BackupWorker(
            appContext = appContext,
            workerParams = workerParameters,
            backupStorage = graph.backupStorage,
            snapshotProvider = graph.databaseSnapshotProvider,
            preferences = graph.backupPreferencesRepository,
            autoBackupController = graph.autoBackupController,
            notificationHelper = graph.backupNotificationHelper,
            snapshotExportRunner = graph.snapshotExportRunner,
        )
    }
}
