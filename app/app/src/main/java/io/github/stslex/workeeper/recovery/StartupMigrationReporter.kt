// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.recovery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [FirebaseCrashlyticsHolder] for the Scenario 2 (startup) recovery
 * non-fatal. Mirrors [RestoreRecoveryReporter] but with the Scenario 2
 * key set per `documentation/feature-specs/backup-recovery.md` →
 * "Crashlytics non-fatals" (`triggered_at = "startup"`,
 * `restore_in_progress = false`, no `backup_version`, plus
 * `startup_failure_reason` and `install_source`).
 *
 * Records a synthetic [StartupMigrationFailure] exception when the
 * pre-flight detected an unrecoverable state via pure file inspection
 * (no Room throw to forward). This is intentional — Crashlytics needs
 * *some* `Throwable` to group reports by, and synthesizing one here
 * makes the dashboard surface the failure mode even though no Room
 * exception was caught.
 */
@Singleton
internal class StartupMigrationReporter @Inject constructor(
    @ApplicationContext private val context: Context,
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

/**
 * Synthetic exception used as the `Throwable` payload for Crashlytics
 * when the startup pre-flight detected unrecoverable state via file
 * inspection (no real Room exception was caught). Lives at file scope
 * so Crashlytics groups by class name without dashboard noise.
 */
internal class StartupMigrationFailure(
    fromSchema: Int,
    toSchema: Int,
    reason: StartupMigrationFailureReason,
) : RuntimeException("Startup migration failure: $fromSchema → $toSchema ($reason)")
