// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Metro-side WorkManager [WorkerFactory].
 *
 * Constructs [BackupWorker] by reading the worker's six app-scoped deps through the typed
 * [BackupWorkerDepsHolder] point-acquisition and calling the constructor directly. This is the DATA layer,
 * so it does NOT use the UI-homed `appDeps<T>()` (that would be a `data → ui` module inversion) — it uses
 * its own typed holder instead.
 *
 * Class-name match: we compare `workerClassName` against [BackupWorker]'s fully-qualified name. Any
 * other worker → `null`, so WorkManager's inherited `createWorkerWithDefaultFallback` constructs it via
 * the default reflection factory (verified against work-runtime 2.10.0 `WorkerFactory.kt`). No
 * `DelegatingWorkerFactory` needed — `BackupWorker` is the only worker in the app.
 */
class MetroWorkerFactory(
    private val appContext: Context,
) : WorkerFactory() {

    // Read once, off the application context — the same singletons the app graph holds. The cast is safe by
    // construction: the process Application (BaseApplication) implements BackupWorkerDepsHolder.
    private val deps: BackupWorkerDeps by lazy {
        (appContext.applicationContext as BackupWorkerDepsHolder).backupWorkerDeps()
    }

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
            backupStorage = deps.backupStorage,
            snapshotProvider = deps.databaseSnapshotProvider,
            preferences = deps.backupPreferencesRepository,
            autoBackupController = deps.autoBackupController,
            notificationHelper = deps.backupNotificationHelper,
            snapshotExportRunner = deps.snapshotExportRunner,
        )
    }
}
