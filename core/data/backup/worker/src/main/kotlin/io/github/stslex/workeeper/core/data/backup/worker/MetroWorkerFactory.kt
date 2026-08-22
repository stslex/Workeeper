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
 * **Lease admission (Phase 5 R2, closed-admission quiesce).** WorkManager caches THIS factory
 * for the process, so a construction-time capture of the deps would pin generation 1's graph
 * forever. Instead every [createWorker] invocation ACQUIRES an admission lease — deps and lease
 * in one atomic step through the typed holder — so the worker is coherently bound to exactly one
 * generation, a replacement transition's quiesce awaits it (constructed-but-not-yet-RUNNING
 * included), and no worker can capture an outgoing generation's dependencies after admission
 * closed: the acquisition PARKS on WorkManager's serial task-executor thread for the bounded
 * transition window and then binds against the freshly published generation.
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
        // Atomic admission per invocation; the cast is safe by construction — the process
        // Application (BaseApplication) implements BackupWorkerDepsHolder. The worker releases
        // the lease in doWork's finally.
        val lease: BackupWorkLease =
            (this.appContext.applicationContext as BackupWorkerDepsHolder).acquireBackupWorkLease()
        return BackupWorker(
            appContext = appContext,
            workerParams = workerParameters,
            workLease = lease,
        )
    }
}
