// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.recovery

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a plain-text diagnostic file for the restore-failure path and
 * returns a [Uri] that can be shared via `Intent.ACTION_SEND` against the
 * app's existing FileProvider (`${applicationId}.fileprovider`, paths in
 * `res/xml/file_provider_paths.xml` → `recovery_diagnostics`).
 *
 * Format follows `documentation/feature-specs/backup-recovery.md` →
 * "Diagnostic file contents". Scenario 1 (restore-time) fields only in this
 * PR; Scenario 2 fields (install source, last successful startup) land with
 * PR-E.
 *
 * The exception type / message / stacktrace are passed in explicitly because
 * the failure that triggered the diagnostic was already caught and recorded
 * by [RestoreRecoveryReporter] — the caller has the live `Throwable` and
 * decides what to include.
 */
@Singleton
internal class RestoreDiagnosticsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotProvider: DatabaseSnapshotProvider,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * Writes the diagnostic file to `cache/recovery_diagnostics/` and returns
     * a content URI grantable via `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
     * Returns `null` when the write fails (caller treats it as "the share
     * intent cannot be offered this time").
     */
    suspend fun exportRestoreFailure(
        exception: Throwable?,
        context: RestoreInProgressContext?,
        appVersionName: String,
        appVersionCode: Long,
    ): Uri? = withContext(dispatcher) {
        runCatching {
            val outDir = File(this@RestoreDiagnosticsExporter.context.cacheDir, DIR_NAME).apply {
                mkdirs()
            }
            val outFile = File(outDir, fileName())
            outFile.writeText(
                buildText(
                    exception = exception,
                    inProgressContext = context,
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                ),
                Charsets.UTF_8,
            )
            FileProvider.getUriForFile(
                this@RestoreDiagnosticsExporter.context,
                "${this@RestoreDiagnosticsExporter.context.packageName}.fileprovider",
                outFile,
            )
        }.getOrNull()
    }

    private fun buildText(
        exception: Throwable?,
        inProgressContext: RestoreInProgressContext?,
        appVersionName: String,
        appVersionCode: Long,
    ): String = buildString {
        appendLine("Workeeper restore diagnostic")
        appendLine("Generated: ${formatIsoUtc(System.currentTimeMillis())}")
        appendLine("Scenario: restore-time (Scenario 1)")
        appendLine()
        appendLine("== App ==")
        appendLine("versionName: $appVersionName")
        appendLine("versionCode: $appVersionCode")
        appendLine("currentSchemaVersion: $APP_DATABASE_VERSION")
        appendLine()
        appendLine("== Device ==")
        appendLine("model: ${Build.MODEL}")
        appendLine("manufacturer: ${Build.MANUFACTURER}")
        appendLine("androidApi: ${Build.VERSION.SDK_INT}")
        appendLine()
        appendLine("== Migrations ==")
        appendLine("registered: ${snapshotProvider.availableMigrationsLabel()}")
        appendLine()
        appendLine("== Restore context ==")
        if (inProgressContext != null) {
            appendLine("backupSchemaVersion: ${inProgressContext.backupSchemaVersion}")
            appendLine(
                "backupCreatedAt: ${formatIsoUtc(inProgressContext.backupCreatedAtEpochMs)}",
            )
            appendLine("backupAppVersion: ${inProgressContext.backupAppVersion}")
            appendLine(
                "restoreStartedAt: ${formatIsoUtc(inProgressContext.startedAtEpochMs)}",
            )
        } else {
            appendLine("(no in-progress context — flag was set but payload missing)")
        }
        appendLine()
        appendLine("== Exception ==")
        if (exception != null) {
            appendLine("type: ${exception::class.java.name}")
            appendLine("message: ${exception.message ?: "(none)"}")
            appendLine()
            appendLine("Stacktrace (first $STACKTRACE_FRAME_LIMIT frames):")
            exception.stackTrace
                .take(STACKTRACE_FRAME_LIMIT)
                .forEach { appendLine("  at $it") }
        } else {
            appendLine("(no captured exception)")
        }
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        return "workeeper_restore_diagnostic_$stamp.txt"
    }

    private fun formatIsoUtc(epochMs: Long): String =
        SimpleDateFormat(ISO_8601_PATTERN, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))

    private companion object {
        const val DIR_NAME = "recovery_diagnostics"
        const val ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
        const val STACKTRACE_FRAME_LIMIT = 50
    }
}
