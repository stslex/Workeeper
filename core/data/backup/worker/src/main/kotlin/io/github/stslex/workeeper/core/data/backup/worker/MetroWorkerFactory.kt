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
 * **No generation capture (Phase 5 R2, spec §8.4/§8.6).** WorkManager caches THIS factory for
 * the process AND may construct workers it never starts (cancelled before dispatch, constraint
 * races) — so the factory must hold NOTHING a runtime transition would have to wait for.
 * Construction is dependency-free; admission happens as the FIRST operation inside
 * [BackupWorker]'s `doWork` ([BackupWorkerDepsHolder.awaitBackupWorkLease]), which is the only
 * point a run binds to a generation.
 */
class MetroWorkerFactory : WorkerFactory() {

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
        )
    }
}
