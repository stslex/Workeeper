// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import java.io.File

/**
 * Single source of truth for backup execution, shared by the periodic and one-time work names.
 * Never mutates the live database; it reads a WAL-checkpointed cache copy. See the backup spec.
 */
// Dependency-free construction: a WorkManager-created idle worker must not capture a generation.
internal class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val logger = Log.tag(TAG)

    override suspend fun doWork(): Result {
        // Admission is first so dependencies and lease bind to the same generation.
        val lease = (applicationContext as BackupWorkerDepsHolder).awaitBackupWorkLease()
        if (lease == null) {
            // Refused BEFORE setLastAttempt: this process cannot prove what its database holds,
            // and uploading it would rotate a good Drive copy away. `failure` over `retry` so a
            // periodic name resets to its interval instead of backing off into a wake loop.
            logger.w { "worker admission is sealed — this process routed to recovery" }
            return Result.failure()
        }
        return try {
            runAdmittedWork(lease.deps)
        } finally {
            lease.release()
        }
    }

    private suspend fun runAdmittedWork(deps: BackupWorkerDeps): Result {
        val now = System.currentTimeMillis()
        deps.backupPreferencesRepository.setLastAttempt(now)

        val tempFile = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, applicationContext.cacheDir)
        return try {
            val result = executeBackup(deps, tempFile)
            // GUARD: AWAIT the snapshot so WorkManager keeps the execution window alive; a
            // detached launch would race process death. runCatching keeps the Result unchanged.
            runCatching { deps.snapshotExportRunner.runIfEligibleAwaiting() }
            result
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun executeBackup(deps: BackupWorkerDeps, tempFile: File): Result {
        when (val capture = deps.databaseSnapshotProvider.captureSnapshot(tempFile)) {
            is BackupResult.Success -> Unit
            is BackupResult.Failure -> return handleFailure(deps, capture.error)
        }

        val manifest = BackupManifest(
            appVersion = readVersionName(),
            dbSchemaVersion = deps.databaseSnapshotProvider.currentSchemaVersion(),
            createdAtEpochMs = System.currentTimeMillis(),
            dbFileSizeBytes = tempFile.length(),
            deviceModel = Build.MODEL,
        )

        return when (val upload = deps.backupStorage.uploadBackup(tempFile, manifest)) {
            is BackupResult.Success -> handleSuccess(deps)
            is BackupResult.Failure -> handleFailure(deps, upload.error)
        }
    }

    private suspend fun handleSuccess(deps: BackupWorkerDeps): Result {
        val now = System.currentTimeMillis()
        deps.backupPreferencesRepository.setLastSuccess(now)
        deps.backupPreferencesRepository.setLastError(null)
        deps.backupNotificationHelper.cancelAuthPaused()
        return Result.success()
    }

    private suspend fun handleFailure(deps: BackupWorkerDeps, error: BackupError): Result {
        logger.w { "backup failed: $error" }
        deps.backupPreferencesRepository.setLastError(BackupErrorCode.from(error))
        return when (error) {
            BackupError.AuthRevoked -> {
                deps.autoBackupController.cancelPeriodic()
                deps.backupNotificationHelper.showAuthPaused()
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
