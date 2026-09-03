// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.diagnostics

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider

/**
 * Crashlytics non-fatal reporter for Scenario 1 (restore-time) failures.
 * See documentation/feature-specs/backup-recovery.md → "Crashlytics non-fatals".
 */
@SingleIn(AppScope::class)
internal class RestoreRecoveryReporter @Inject constructor(
    private val snapshotProvider: DatabaseSnapshotProvider,
) {

    fun recordRestoreTimeFailure(
        exception: Throwable,
        context: RestoreInProgressContext,
        appVersionName: String,
    ) {
        FirebaseCrashlyticsHolder.apply {
            setCustomKey(KEY_MIGRATION_FROM_SCHEMA, context.backupSchemaVersion)
            setCustomKey(KEY_MIGRATION_TO_SCHEMA, APP_DATABASE_VERSION)
            setCustomKey(KEY_AVAILABLE_MIGRATIONS, snapshotProvider.availableMigrationsLabel())
            setCustomKey(KEY_APP_VERSION, appVersionName)
            setCustomKey(KEY_TRIGGERED_AT, TRIGGERED_AT_RESTORE)
            setCustomKey(KEY_RESTORE_IN_PROGRESS, true)
            setCustomKey(KEY_BACKUP_VERSION, context.backupSchemaVersion)
        }
        FirebaseCrashlyticsHolder.recordException(exception, tag = TAG)
    }

    private companion object {
        const val TAG = "RestoreRecovery"

        const val KEY_MIGRATION_FROM_SCHEMA = "migration_from_schema"
        const val KEY_MIGRATION_TO_SCHEMA = "migration_to_schema"
        const val KEY_AVAILABLE_MIGRATIONS = "available_migrations"
        const val KEY_APP_VERSION = "app_version"
        const val KEY_TRIGGERED_AT = "triggered_at"
        const val KEY_RESTORE_IN_PROGRESS = "restore_in_progress"
        const val KEY_BACKUP_VERSION = "backup_version"

        const val TRIGGERED_AT_RESTORE = "restore"
    }
}
