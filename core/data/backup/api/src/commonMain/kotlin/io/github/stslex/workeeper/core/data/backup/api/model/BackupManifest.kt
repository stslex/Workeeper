// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Sidecar metadata stored alongside every uploaded backup. Used at restore time to
 * decide whether the backup is compatible with the installed app
 * (see [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.BackupTooNew]
 * and [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.MissingMigrationPath])
 * and to render human-readable backup entries in the UI.
 *
 * All fields are captured at backup-creation time on the device that produced the
 * archive; none of them are mutated server-side.
 */
data class BackupManifest(
    val appVersion: String,
    val dbSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val dbFileSizeBytes: Long,
    val deviceModel: String?,
)
