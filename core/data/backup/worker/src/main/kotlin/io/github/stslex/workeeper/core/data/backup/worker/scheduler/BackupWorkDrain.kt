// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker.scheduler

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first

/**
 * The two unique-work names [BackupScheduler] enqueues under — hoisted from its companion so the
 * Quiescing drain below observes exactly the names the scheduler writes (one definition, no
 * copy-drift). WorkManager's own persistence carries the periodic chain across processes; these
 * strings are its wire format — never rename.
 */
internal const val UNIQUE_PERIODIC_WORK_NAME = "auto_backup"
internal const val UNIQUE_ONE_TIME_WORK_NAME = "one_time_backup"

/**
 * Phase 5 Quiescing drain (`kmp-phase-5-startup-processor.md` §8.4 step 2): suspends until no
 * backup worker is RUNNING. An in-flight `BackupWorker` holds the six dependencies it was
 * constructed with — including the DB-bound snapshot provider — for its whole run, so a database
 * replacement must not close the generation's database under it.
 *
 * Drain, deliberately NOT cancel: `cancelUniqueWork` on the periodic name would remove the
 * scheduled chain itself, silently unscheduling auto-backup — awaiting RUNNING → non-RUNNING
 * leaves WorkManager's persisted schedule exactly as a process restart does today. The caller
 * bounds this with a timeout; on timeout the replacement aborts BEFORE the close, so the worker
 * simply finishes on the still-open database.
 */
suspend fun awaitBackupWorkersIdle(workManager: WorkManager) {
    listOf(UNIQUE_PERIODIC_WORK_NAME, UNIQUE_ONE_TIME_WORK_NAME).forEach { name ->
        workManager.getWorkInfosForUniqueWorkFlow(name)
            .first { infos -> infos.none { it.state == WorkInfo.State.RUNNING } }
    }
}
