// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker.scheduler

/**
 * The two unique-work names [BackupScheduler] enqueues under — hoisted from its companion so
 * every observer reads exactly the names the scheduler writes (one definition, no copy-drift).
 * WorkManager's own persistence carries the periodic chain across processes; these strings are
 * its wire format — never rename.
 *
 * The Phase 5 snapshot-style WorkInfo drain that used to live here is gone: it could only
 * observe a moment with no RUNNING worker, and a worker admitted right after the check would
 * capture the outgoing generation. Closed admission replaced it — see `BackupWorkLease` and
 * `BackupWorkerDepsHolder.acquireBackupWorkLease` (deps + quiesce-awaited lease in one atomic
 * step; constructed-but-not-yet-RUNNING workers included).
 */
internal const val UNIQUE_PERIODIC_WORK_NAME = "auto_backup"
internal const val UNIQUE_ONE_TIME_WORK_NAME = "one_time_backup"
