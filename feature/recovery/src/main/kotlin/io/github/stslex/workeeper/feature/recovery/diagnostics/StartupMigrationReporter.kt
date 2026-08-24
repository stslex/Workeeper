// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationFailureReason

/**
 * Crashlytics non-fatal reporter for Scenario 2 (startup) recovery.
 * See documentation/feature-specs/backup-recovery.md → "Crashlytics non-fatals".
 */
@SingleIn(AppScope::class)
internal class StartupMigrationReporter @Inject constructor(
    private val context: Context,
    private val snapshotProvider: DatabaseSnapshotProvider,
) {

    fun recordStartupMigrationFailure(
        exception: Throwable?,
        fromSchema: Int,
        toSchema: Int,
        reason: StartupMigrationFailureReason,
    ) {
        FirebaseCrashlyticsHolder.apply {
            setCustomKey(KEY_MIGRATION_FROM_SCHEMA, fromSchema)
            setCustomKey(KEY_MIGRATION_TO_SCHEMA, toSchema)
            setCustomKey(KEY_AVAILABLE_MIGRATIONS, snapshotProvider.availableMigrationsLabel())
            setCustomKey(KEY_APP_VERSION, readVersionName())
            setCustomKey(KEY_TRIGGERED_AT, TRIGGERED_AT_STARTUP)
            setCustomKey(KEY_RESTORE_IN_PROGRESS, false)
            setCustomKey(KEY_STARTUP_FAILURE_REASON, reason.name)
            setCustomKey(KEY_INSTALL_SOURCE, detectInstallSource())
        }
        FirebaseCrashlyticsHolder.recordException(
            throwable = exception ?: StartupMigrationFailure(fromSchema, toSchema, reason),
            tag = TAG,
        )
    }

    private fun readVersionName(): String =
        runCatching { readPackageInfo().versionName.orEmpty() }.getOrDefault("")

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

    private fun readPackageInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private companion object {
        const val TAG = "StartupMigration"

        const val KEY_MIGRATION_FROM_SCHEMA = "migration_from_schema"
        const val KEY_MIGRATION_TO_SCHEMA = "migration_to_schema"
        const val KEY_AVAILABLE_MIGRATIONS = "available_migrations"
        const val KEY_APP_VERSION = "app_version"
        const val KEY_TRIGGERED_AT = "triggered_at"
        const val KEY_RESTORE_IN_PROGRESS = "restore_in_progress"
        const val KEY_STARTUP_FAILURE_REASON = "startup_failure_reason"
        const val KEY_INSTALL_SOURCE = "install_source"

        const val TRIGGERED_AT_STARTUP = "startup"
        const val INSTALL_SOURCE_UNKNOWN = "unknown"
    }
}

/** Synthetic payload for Crashlytics, which groups non-fatals by `Throwable` class. */
internal class StartupMigrationFailure(
    fromSchema: Int,
    toSchema: Int,
    reason: StartupMigrationFailureReason,
) : RuntimeException("Startup migration failure: $fromSchema → $toSchema ($reason)")
