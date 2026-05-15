// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

/**
 * Snapshot of the manifest fields captured the moment Restore was confirmed
 * (before the file replace). Persisted to DataStore alongside the
 * `restore_in_progress` flag so the post-restart pre-flight (Scenario 1)
 * can attach them to the Crashlytics non-fatal and the diagnostic export
 * without re-reading the (possibly corrupt) restored database.
 *
 * Spec: `documentation/feature-specs/backup-recovery.md` →
 * "Crashlytics non-fatals" + "Diagnostic file contents".
 */
data class RestoreInProgressContext(
    /** Backup's `db_schema_version` from the manifest — the version we tried to restore from. */
    val backupSchemaVersion: Int,
    /** Backup's `created_at_epoch_ms` — when the user originally captured it. */
    val backupCreatedAtEpochMs: Long,
    /** Backup's `app_version` — the app version that produced the backup. */
    val backupAppVersion: String,
    /** Wall-clock time of the restore tap. Used to date the pre-restore data for undo UI. */
    val startedAtEpochMs: Long,
)
