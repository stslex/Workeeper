// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import io.github.stslex.workeeper.core.data.backup.worker.di.BackupWorkerHiltEntryPoint

/**
 * Metro-side WorkManager [WorkerFactory] (App-Scope Collapse Step 2 — reversible standup, Design B).
 *
 * Constructs [BackupWorker] WITHOUT Hilt's assisted-injection machinery: it bridge-reads the worker's
 * six app-scoped `@Singleton` deps from the Hilt `SingletonComponent` via [BackupWorkerHiltEntryPoint]
 * (the `SettingsHiltEntryPoint` bound-instance pattern) and calls the constructor directly. Metro never
 * processes `BackupWorker`'s constructor, so its Dagger `@AssistedInject` + `@HiltWorker` are untouched
 * and Hilt's `HiltWorkerFactory` keeps working in parallel until the Step 6 cut.
 *
 * DORMANT UNTIL STEP 6: this factory is stood up but NOT wired. `BaseApplication`'s
 * `Configuration.Provider` still returns Hilt's `HiltWorkerFactory`, so at runtime WorkManager routes
 * every worker through Hilt exactly as before — this class constructs nothing until Step 6 flips
 * `Configuration.Provider` to it (and drops `@HiltWorker`).
 *
 * Class-name match mirrors `HiltWorkerFactory` (which keys a `Map<String, …>` by `workerClassName`):
 * we compare `workerClassName` against [BackupWorker]'s fully-qualified name. Any other worker → `null`,
 * so WorkManager's inherited `createWorkerWithDefaultFallback` constructs it via the default reflection
 * factory (verified against work-runtime 2.10.0 `WorkerFactory.kt`). No `DelegatingWorkerFactory` needed —
 * `BackupWorker` is the only worker in the app.
 */
internal class MetroWorkerFactory(
    private val appContext: Context,
) : WorkerFactory() {

    // Read once, off the application context — the same @Singleton instances Hilt already holds.
    private val entryPoint: BackupWorkerHiltEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            BackupWorkerHiltEntryPoint::class.java,
        )
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
            backupStorage = entryPoint.backupStorage(),
            snapshotProvider = entryPoint.databaseSnapshotProvider(),
            preferences = entryPoint.backupPreferencesRepository(),
            autoBackupController = entryPoint.autoBackupController(),
            notificationHelper = entryPoint.backupNotificationHelper(),
            snapshotExportRunner = entryPoint.snapshotExportRunner(),
        )
    }
}
