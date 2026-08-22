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
 * the default reflection factory (verified against work-runtime 2.11.2 `WorkerFactory.kt`). No
 * `DelegatingWorkerFactory` needed — `BackupWorker` is the only worker in the app.
 *
 * **Per-invocation single read (Phase 5 R2, spec §8.6).** WorkManager caches THIS factory for the
 * process, so a construction-time capture of the deps would pin generation 1's graph forever —
 * every worker after a runtime-generation replacement would run against a terminal generation's
 * dependencies. Instead the holder is read ONCE per [createWorker] invocation and all six deps
 * come from that single returned graph: the worker is coherently bound to exactly one generation
 * (never torn across two), and workers created after a replacement get the CURRENT generation. A
 * worker already inside `doWork()` keeps its construction-time generation for the whole run —
 * that live capture is the replacement transaction's worker-drain concern, not this factory's.
 */
class MetroWorkerFactory(
    private val appContext: Context,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != BackupWorker::class.java.name) {
            // Not our worker — return null so WorkManager falls through to the default factory.
            return null
        }
        // One holder read per invocation; the cast is safe by construction — the process
        // Application (BaseApplication) implements BackupWorkerDepsHolder.
        val deps: BackupWorkerDeps =
            (this.appContext.applicationContext as BackupWorkerDepsHolder).backupWorkerDeps()
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
