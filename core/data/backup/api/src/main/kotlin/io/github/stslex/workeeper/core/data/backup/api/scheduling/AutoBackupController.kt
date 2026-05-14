package io.github.stslex.workeeper.core.data.backup.api.scheduling

import kotlinx.coroutines.flow.Flow

/**
 * Contract over WorkManager for the two backup work entry points the app
 * exposes. Implemented by the singleton `BackupScheduler` in
 * `core/data/backup/worker`; consumed from feature code that must not depend on
 * `androidx.work.*` directly.
 *
 * Two work names live in parallel and are intentionally independent:
 *
 * - **Periodic** — scheduled by [schedulePeriodic] when [BackupPreferences.schedule]
 *   is [BackupSchedule.Daily] or [BackupSchedule.Weekly]. Cancelled by
 *   [cancelPeriodic] when the user switches to [BackupSchedule.ManualOnly] OR
 *   when the worker observes [BackupErrorCode.AuthRevoked].
 *
 * - **One-time** — fired by [enqueueOneTime] on the "Backup now" tap and on the
 *   first-sign-in bootstrap. Independent of the periodic work — cancelling
 *   periodic does NOT cancel an in-flight one-time backup, and vice versa.
 *
 * [observePeriodicStatus] / [observeOneTimeStatus] surface the live
 * `WorkInfo` lists so the UI can render "Next backup in N" / "Backup running"
 * without polling.
 */
interface AutoBackupController {

    suspend fun schedulePeriodic(preferences: BackupPreferences)

    suspend fun cancelPeriodic()

    suspend fun enqueueOneTime()

    fun observePeriodicStatus(): Flow<List<AutoBackupWorkInfo>>

    fun observeOneTimeStatus(): Flow<List<AutoBackupWorkInfo>>
}

/**
 * Provider-neutral mirror of the bits of `androidx.work.WorkInfo` the UI needs.
 * Keeping the WorkManager type out of api/ avoids dragging the work-runtime
 * dependency into feature modules that only read backup status.
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
