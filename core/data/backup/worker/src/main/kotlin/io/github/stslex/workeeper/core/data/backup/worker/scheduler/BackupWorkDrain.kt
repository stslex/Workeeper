// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker.scheduler

/**
 * The two unique-work names [BackupScheduler] enqueues under, hoisted so observers and the
 * scheduler share one definition. GUARD: WorkManager persists by these strings — never rename.
 */
internal const val UNIQUE_PERIODIC_WORK_NAME = "auto_backup"
internal const val UNIQUE_ONE_TIME_WORK_NAME = "one_time_backup"
