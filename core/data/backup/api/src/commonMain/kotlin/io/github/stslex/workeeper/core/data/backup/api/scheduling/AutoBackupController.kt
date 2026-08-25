// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

import kotlinx.coroutines.flow.Flow

/**
 * Contract over WorkManager for the periodic and one-time backup work names, which are
 * independent: cancelling one never cancels the other. See documentation/feature-specs/backup.md.
 */
interface AutoBackupController {

    suspend fun schedulePeriodic(preferences: BackupPreferences)

    suspend fun cancelPeriodic()

    suspend fun enqueueOneTime()

    fun observePeriodicStatus(): Flow<List<AutoBackupWorkInfo>>

    fun observeOneTimeStatus(): Flow<List<AutoBackupWorkInfo>>
}

/**
 * Provider-neutral mirror of the `androidx.work.WorkInfo` bits the UI needs, so feature modules
 * need no work-runtime dependency.
 */
data class AutoBackupWorkInfo(
    val state: State,
    val nextScheduleTimeEpochMs: Long?,
) {

    enum class State {
        Enqueued,
        Running,
        Succeeded,
        Failed,
        Blocked,
        Cancelled,
    }
}
