// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
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

/**
 * Writes plain-text diagnostic files for either recovery flow and returns
 * a [Uri] grantable via `Intent.FLAG_GRANT_READ_URI_PERMISSION` for the
 * share-chooser. Both flows share the same chrome (header, app/device,
 * migrations) and diverge on the scenario-specific section.
 *
 * Format follows `documentation/feature-specs/backup-recovery.md` →
 * "Diagnostic file contents".
 *
 * Renamed from `RestoreDiagnosticsExporter` when Scenario 2 added a
 * second use case — same class, same FileProvider authority, same
 * `cache/recovery_diagnostics/` output directory, just two entry points.
 *
 * DB-safety: this class is constructor-injected with
 * [DatabaseSnapshotProvider], but only calls
 * [DatabaseSnapshotProvider.availableMigrationsLabel] — a pure-Kotlin
 * read of the registered MIGRATIONS array. No Room access; safe to
 * inject into `RecoveryActivity`'s graph.
 *
 * Metro-owned via `@ContributesBinding(AppScope)`, bound to the
 * [RecoveryDiagnosticsExporter] api interface (in `core:data:backup:api`). Public for cross-module
 * aggregation (D1; never hand-construct — resolve via DI). Its `Context` is the graph's
 * `create(applicationContext)` root (plain param); `@IODispatcher` resolves
 * from `DispatchersBindingContainer`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class RecoveryDiagnosticsExporterImpl @Inject constructor(
    private val context: Context,
    private val snapshotProvider: DatabaseSnapshotProvider,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : RecoveryDiagnosticsExporter {

    /**
     * Scenario 1 (restore-time) variant. Writes `cache/recovery_diagnostics/
     * workeeper_restore_diagnostic_<stamp>.txt` and returns its content URI.
     * Returns `null` if the write failed (caller hides the share action).
     */
    override suspend fun exportRestoreFailure(
        exception: Throwable?,
        context: RestoreInProgressContext?,
        appVersionName: String,
        appVersionCode: Long,
    ): Uri? = withContext(dispatcher) {
        runCatching {
            val outFile = newFile(prefix = "workeeper_restore_diagnostic")
            outFile.writeText(
                text = buildRestoreText(
                    exception = exception,
                    inProgressContext = context,
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                ),
                charset = Charsets.UTF_8,
            )
            uriFor(outFile)
        }.getOrNull()
    }

    /**
     * Scenario 2 (startup-time) variant. Writes
     * `cache/recovery_diagnostics/workeeper_startup_diagnostic_<stamp>.txt`
     * and returns its content URI. Reads version + install-source info
     * from the app context directly so `RecoveryActivity` does not need to
     * plumb them in.
     */
    override suspend fun exportStartupMigrationFailure(): Uri? = withContext(dispatcher) {
        runCatching {
            val outFile = newFile(prefix = "workeeper_startup_diagnostic")
            outFile.writeText(text = buildStartupText(), charset = Charsets.UTF_8)
            uriFor(outFile)
        }.getOrNull()
    }

    private fun buildRestoreText(
        exception: Throwable?,
        inProgressContext: RestoreInProgressContext?,
        appVersionName: String,
        appVersionCode: Long,
    ): String = buildString {
        appendHeader("restore-time (Scenario 1)")
        appendAppSection(appVersionName, appVersionCode)
        appendDeviceSection()
        appendMigrationsSection()
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
        appendExceptionSection(exception)
    }

    private fun buildStartupText(): String = buildString {
        appendHeader("startup-time (Scenario 2)")
        val info = readPackageInfo()
        appendAppSection(
            appVersionName = info.versionName.orEmpty(),
            appVersionCode = readVersionCode(info),
        )
        appendDeviceSection()
        appendMigrationsSection()
        appendLine("== Install ==")
        appendLine("installSource: ${detectInstallSource()}")
        appendLine()
        appendExceptionSection(exception = null)
    }

    private fun StringBuilder.appendHeader(scenario: String) {
        appendLine("Workeeper recovery diagnostic")
        appendLine("Generated: ${formatIsoUtc(System.currentTimeMillis())}")
        appendLine("Scenario: $scenario")
        appendLine()
    }

    private fun StringBuilder.appendAppSection(appVersionName: String, appVersionCode: Long) {
        appendLine("== App ==")
        appendLine("versionName: $appVersionName")
        appendLine("versionCode: $appVersionCode")
        appendLine("currentSchemaVersion: $APP_DATABASE_VERSION")
        appendLine()
    }

    private fun StringBuilder.appendDeviceSection() {
        appendLine("== Device ==")
        appendLine("model: ${Build.MODEL}")
        appendLine("manufacturer: ${Build.MANUFACTURER}")
        appendLine("androidApi: ${Build.VERSION.SDK_INT}")
        appendLine()
    }

    private fun StringBuilder.appendMigrationsSection() {
        appendLine("== Migrations ==")
        appendLine("registered: ${snapshotProvider.availableMigrationsLabel()}")
        appendLine()
    }

    private fun StringBuilder.appendExceptionSection(exception: Throwable?) {
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

    private fun newFile(prefix: String): File {
        val outDir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        return File(outDir, "${prefix}_${timestamp()}.txt")
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun timestamp(): String = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

    private fun formatIsoUtc(epochMs: Long): String =
        SimpleDateFormat(ISO_8601_PATTERN, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))

    private fun readPackageInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun readVersionCode(info: android.content.pm.PackageInfo): Long = info.longVersionCode

    private fun detectInstallSource(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName ?: INSTALL_SOURCE_UNKNOWN
        } else {
            @Suppress("DEPRECATION")
            context.packageManager
                .getInstallerPackageName(context.packageName)
                ?: INSTALL_SOURCE_UNKNOWN
        }
    }.getOrElse { INSTALL_SOURCE_UNKNOWN }

    private companion object {
        const val DIR_NAME = "recovery_diagnostics"
        const val ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
        const val STACKTRACE_FRAME_LIMIT = 50
        const val INSTALL_SOURCE_UNKNOWN = "unknown"
    }
}
