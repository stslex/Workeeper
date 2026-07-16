// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default [SnapshotExportRunner]: gate on toggle + `drive.file` grant, then export the JSON
 * and upload it. The whole body is wrapped so it never throws to the caller (D2). Only
 * UNEXPECTED failures (serialization / IO bugs) are recorded as Crashlytics non-fatals (via
 * [Log.e]); transient typed [BackupError]s (network / auth / quota / not-authenticated) are
 * logged only (via [Log.w], which does not record a non-fatal).
 *
 * Metro-owned via `@ContributesBinding(AppScope)` on the (public) impl. All deps are graph-resolvable:
 * `BackupPreferencesRepository` / `BackupAuth` / `DatabaseJsonExporter` / `SnapshotStorage` are
 * `@ContributesBinding`; `Context` is the plain `create()` bound-instance root; `@IODispatcher` is the
 * direct `Dispatchers.IO`. Public for cross-module aggregation (D1).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class SnapshotExportRunnerImpl @Inject constructor(
    private val preferences: BackupPreferencesRepository,
    private val backupAuth: BackupAuth,
    private val exporter: DatabaseJsonExporter,
    private val snapshotStorage: SnapshotStorage,
    private val context: Context,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : SnapshotExportRunner {

    private val logger = Log.tag(TAG)

    // App-lifetime scope for the FOREGROUND fire-and-forget path so the manual backup returns
    // immediately and is never delayed by the DB-export + visible-Drive upload (D2). The worker
    // instead awaits via runIfEligibleAwaiting() to keep its wakelock window. Best-effort — if the
    // process is reclaimed before it finishes, the next backup re-exports (losing a snapshot loses
    // nothing recoverable).
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Serializes the export's Drive mutation across the manual + worker triggers (both funnel
    // through this singleton). Without it, two overlapping runs could create duplicate Workeeper/
    // folders (resolveFolderId is check-then-act) or violate the rotation cap (list-then-delete).
    // In-process is sufficient: WorkManager runs the worker in the app process.
    private val exportMutex = Mutex()

    override fun runIfEligible() {
        scope.launch { runExport() }
    }

    override suspend fun runIfEligibleAwaiting() {
        runExport()
    }

    override suspend fun clearSnapshots() {
        runCatching {
            // Serialize with the export paths: deleting while an upload is mid-rotation could
            // delete the wrong set or race the folder lookup. The storage gates on the grant.
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
