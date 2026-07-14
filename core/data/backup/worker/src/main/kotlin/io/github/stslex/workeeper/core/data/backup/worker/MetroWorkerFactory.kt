// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.di.appGraphContract

/**
 * Metro-side WorkManager [WorkerFactory] (App-Scope Collapse Step 2 — reversible standup, Design B).
 *
 * Constructs [BackupWorker] WITHOUT Hilt's assisted-injection machinery: it reads the worker's six
 * app-scoped deps from the Metro app graph via `appContext.appGraphContract()` (App-Scope Collapse
 * Step 6, P-WORKER — Hilt-free, replacing the `BackupWorkerHiltEntryPoint` bridge) and calls the
 * constructor directly.
 *
 * DORMANT UNTIL THE CUT: this factory is repointed to the graph but NOT yet wired. `BaseApplication`'s
 * `Configuration.Provider` still returns Hilt's `HiltWorkerFactory`, so at runtime WorkManager routes
 * every worker through Hilt exactly as before — this class constructs nothing until the cut flips
 * `Configuration.Provider` to it (and drops `@HiltWorker`).
 *
 * Class-name match mirrors `HiltWorkerFactory` (which keys a `Map<String, …>` by `workerClassName`):
 * we compare `workerClassName` against [BackupWorker]'s fully-qualified name. Any other worker → `null`,
 * so WorkManager's inherited `createWorkerWithDefaultFallback` constructs it via the default reflection
 * factory (verified against work-runtime 2.10.0 `WorkerFactory.kt`). No `DelegatingWorkerFactory` needed —
 * `BackupWorker` is the only worker in the app.
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
