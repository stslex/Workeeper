package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import java.io.File

/**
 * Single source of truth for backup execution. Used by BOTH the periodic
 * auto-backup work (`UNIQUE_PERIODIC_NAME` in [BackupScheduler]) and the
 * one-time manual-backup work (`UNIQUE_ONE_TIME_NAME`); the two work names
 * route to the same worker class.
 *
 * Failure handling:
 * - [BackupError.AuthRevoked] → cancel the periodic work entirely, show the
 *   auth-paused notification, return [Result.failure]. The user must re-sign-in
 *   to resume auto-backup.
 * - [BackupError.NetworkUnavailable] → [Result.retry]. WorkManager will retry
 *   with exponential backoff when the network constraint is satisfied.
 * - [BackupError.StorageQuotaExceeded] → [Result.failure]. Non-retryable
 *   without user action.
 * - All other failures → [Result.retry] with WorkManager's default backoff.
 *
 * The worker never mutates the live database — it reads via
 * [DatabaseSnapshotProvider.captureSnapshot] which produces a WAL-checkpointed
 * copy in [Context.getCacheDir].
 */
@HiltWorker
internal class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupStorage: BackupStorage,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val preferences: BackupPreferencesRepository,
    private val autoBackupController: AutoBackupController,
    private val notificationHelper: BackupNotificationHelper,
) : CoroutineWorker(appContext, workerParams) {

    private val logger = Log.tag(TAG)

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        preferences.setLastAttempt(now)

        val tempFile = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, applicationContext.cacheDir)
        return try {
            executeBackup(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun executeBackup(tempFile: File): Result {
        when (val capture = snapshotProvider.captureSnapshot(tempFile)) {
            is BackupResult.Success -> Unit
            is BackupResult.Failure -> return handleFailure(capture.error)
        }

        val manifest = BackupManifest(
            appVersion = readVersionName(),
            dbSchemaVersion = snapshotProvider.currentSchemaVersion(),
            createdAtEpochMs = System.currentTimeMillis(),
            dbFileSizeBytes = tempFile.length(),
            deviceModel = Build.MODEL,
        )

        return when (val upload = backupStorage.uploadBackup(tempFile, manifest)) {
            is BackupResult.Success -> handleSuccess()
            is BackupResult.Failure -> handleFailure(upload.error)
        }
    }

    private suspend fun handleSuccess(): Result {
        val now = System.currentTimeMillis()
        preferences.setLastSuccess(now)
        preferences.setLastError(null)
        notificationHelper.cancelAuthPaused()
        return Result.success()
    }

    private suspend fun handleFailure(error: BackupError): Result {
        logger.w { "backup failed: $error" }
        preferences.setLastError(BackupErrorCode.from(error))
        return when (error) {
            BackupError.AuthRevoked -> {
                autoBackupController.cancelPeriodic()
                notificationHelper.showAuthPaused()
                Result.failure()
            }
            BackupError.NetworkUnavailable -> Result.retry()
            BackupError.StorageQuotaExceeded -> Result.failure()
            BackupError.NotAuthenticated -> Result.failure()
            else -> Result.retry()
        }
    }

    private fun readVersionName(): String {
        val context = applicationContext
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName.orEmpty()
    }

    private companion object {
        const val TAG = "BackupWorker"
        const val TEMP_PREFIX = "backup_worker_"
        const val TEMP_SUFFIX = ".db"
    }
}
