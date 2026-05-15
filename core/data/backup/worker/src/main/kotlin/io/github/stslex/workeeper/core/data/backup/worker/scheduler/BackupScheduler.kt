package io.github.stslex.workeeper.core.data.backup.worker.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupWorkInfo
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackupScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val preferencesRepository: BackupPreferencesRepository,
) : AutoBackupController {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    override suspend fun schedulePeriodic(preferences: BackupPreferences) {
        val intervalDays = when (preferences.schedule) {
            BackupSchedule.Daily -> DAILY_INTERVAL_DAYS
            BackupSchedule.Weekly -> WEEKLY_INTERVAL_DAYS
            BackupSchedule.ManualOnly -> {
                cancelPeriodic()
                return
            }
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(preferences.networkType())
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_HOURS,
                TimeUnit.HOURS,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override suspend fun cancelPeriodic() {
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_NAME)
    }

    override suspend fun enqueueOneTime() {
        // One-shot cleanup: cancel any work scheduled under the previous unique
        // name ("manual_backup"). Renaming a WorkManager unique name does not
        // migrate the in-flight work — without this, the old unique work would
        // linger until system cleanup. Cancelling a nonexistent name is a no-op,
        // so this is safe to keep across releases.
        workManager.cancelUniqueWork(LEGACY_ONE_TIME_NAME)

        val prefs = preferencesRepository.observe().first()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(prefs.networkType())
            .build()
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun observePeriodicStatus(): Flow<List<AutoBackupWorkInfo>> = workManager
        .getWorkInfosForUniqueWorkFlow(UNIQUE_PERIODIC_NAME)
        .map { infos -> infos.map(WorkInfo::toAutoBackupInfo) }

    override fun observeOneTimeStatus(): Flow<List<AutoBackupWorkInfo>> = workManager
        .getWorkInfosForUniqueWorkFlow(UNIQUE_ONE_TIME_NAME)
        .map { infos -> infos.map(WorkInfo::toAutoBackupInfo) }

    private fun BackupPreferences.networkType(): NetworkType =
        if (allowOnMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED

    private companion object {
        const val UNIQUE_PERIODIC_NAME = "auto_backup"
        const val UNIQUE_ONE_TIME_NAME = "one_time_backup"
        const val LEGACY_ONE_TIME_NAME = "manual_backup"
        const val DAILY_INTERVAL_DAYS = 1L
        const val WEEKLY_INTERVAL_DAYS = 7L
        const val BACKOFF_HOURS = 1L
    }
}

private fun WorkInfo.toAutoBackupInfo(): AutoBackupWorkInfo = AutoBackupWorkInfo(
    state = when (state) {
        WorkInfo.State.ENQUEUED -> AutoBackupWorkInfo.State.Enqueued
        WorkInfo.State.RUNNING -> AutoBackupWorkInfo.State.Running
        WorkInfo.State.SUCCEEDED -> AutoBackupWorkInfo.State.Succeeded
        WorkInfo.State.FAILED -> AutoBackupWorkInfo.State.Failed
        WorkInfo.State.BLOCKED -> AutoBackupWorkInfo.State.Blocked
        WorkInfo.State.CANCELLED -> AutoBackupWorkInfo.State.Cancelled
    },
    nextScheduleTimeEpochMs = nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE },
)
