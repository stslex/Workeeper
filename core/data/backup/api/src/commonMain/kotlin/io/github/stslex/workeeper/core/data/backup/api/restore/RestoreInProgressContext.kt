// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

/**
 * Manifest fields captured the moment Restore was confirmed, persisted so the post-restart
 * pre-flight can report them without reading the restored database. See the recovery spec.
 */
data class RestoreInProgressContext(
    val backupSchemaVersion: Int,
    val backupCreatedAtEpochMs: Long,
    val backupAppVersion: String,
    /** Wall-clock time of the restore tap. Used to date the pre-restore data for undo UI. */
    val startedAtEpochMs: Long,
)
