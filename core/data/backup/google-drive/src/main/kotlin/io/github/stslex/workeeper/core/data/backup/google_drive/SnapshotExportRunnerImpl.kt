// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.SnapshotStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper
import io.github.stslex.workeeper.core.data.database.export.DatabaseJsonExporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default [SnapshotExportRunner]: gates on toggle + `drive.file` grant, exports and uploads JSON.
 * The body never throws to the caller; only unexpected failures become Crashlytics non-fatals.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class SnapshotExportRunnerImpl @Inject constructor(
    private val preferences: BackupPreferencesRepository,
    private val backupAuth: BackupAuth,
    private val exporter: DatabaseJsonExporter,
    private val snapshotStorage: SnapshotStorage,
    private val context: Context,
    lifetime: AppScopeLifetime,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : SnapshotExportRunner {

    private val logger = Log.tag(TAG)

    // App-lifetime scope for the foreground fire-and-forget path; generation-owned, so an
    // in-flight export is cancelled with its generation instead of racing a replacement.
    private val scope = lifetime.childScope(dispatcher)

    // Serializes the export's Drive mutation across the manual + worker triggers; without it two
    // runs could duplicate the Workeeper/ folder or break the rotation cap.
    private val exportMutex = Mutex()

    override fun runIfEligible() {
        scope.launch { runExport() }
    }

    override suspend fun runIfEligibleAwaiting() {
        runExport()
    }

    override suspend fun clearSnapshots() {
        runCatching {
            // Serialize with the export paths so a delete cannot race an in-flight upload.
            exportMutex.withLock {
                when (val result = snapshotStorage.deleteAllSnapshots()) {
                    is BackupResult.Success -> Unit
                    is BackupResult.Failure -> handleFailure(result.error)
                }
            }
        }.onFailure { t ->
            // Unexpected throwable. Record + swallow — deletion is best-effort housekeeping.
            logger.e(t, "AI snapshot deletion threw")
        }
    }

    private suspend fun runExport() {
        runCatching {
            if (!isEligible()) return@runCatching
            exportMutex.withLock {
                val json = exporter.export(
                    appVersion = readVersionName(),
                    // Cap to match the binary manifest (spec §3); a >100-char model is truncated.
                    deviceModel = Build.MODEL.take(ManifestPropertiesMapper.DEVICE_MODEL_MAX_LEN),
                    exportedAtEpochMs = System.currentTimeMillis(),
                )
                when (val result = snapshotStorage.uploadSnapshot(json)) {
                    is BackupResult.Success -> Unit
                    is BackupResult.Failure -> handleFailure(result.error)
                }
            }
        }.onFailure { t ->
            // Unexpected throwable (e.g. serialization / DB-read bug). Record + swallow.
            logger.e(t, "AI snapshot export threw")
        }
    }

    private suspend fun isEligible(): Boolean = preferences.observe().first().aiExportEnabled &&
        backupAuth.observeDriveFileGranted().first()

    private fun handleFailure(error: BackupError) {
        if (error.isTransient()) {
            logger.w { "AI snapshot export skipped (transient): $error" }
        } else {
            logger.e(error.toThrowable(), "AI snapshot export failed")
        }
    }

    private fun BackupError.isTransient(): Boolean = when (this) {
        BackupError.NetworkUnavailable,
        BackupError.AuthRevoked,
        BackupError.StorageQuotaExceeded,
        BackupError.NotAuthenticated,
        -> true

        else -> false
    }

    private fun BackupError.toThrowable(): Throwable = when (this) {
        is BackupError.Io -> cause
        is BackupError.Unknown -> cause
        else -> IllegalStateException("AI snapshot export error: $this")
    }

    private fun readVersionName(): String {
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
        const val TAG = "SnapshotExportRunner"
    }
}
