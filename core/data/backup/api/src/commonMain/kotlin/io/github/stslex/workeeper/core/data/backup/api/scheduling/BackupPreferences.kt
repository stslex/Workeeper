// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

/**
 * Snapshot of the persisted auto-backup settings and last-attempt status, read by both the worker
 * and the settings UI. [lastError] is non-null only while the latest attempt is a failure.
 */
data class BackupPreferences(
    val schedule: BackupSchedule,
    val allowOnMobileData: Boolean,
    val lastAttemptAtEpochMs: Long,
    val lastSuccessAtEpochMs: Long,
    val lastError: BackupErrorCode?,
    val autoBackupBootstrapped: Boolean,
    /**
     * Whether the user enabled the AI snapshot export. The runner also gates on the actual
     * `drive.file` grant, so it stays inert until both hold.
     */
    val aiExportEnabled: Boolean = false,
) {

    companion object {

        val DEFAULT: BackupPreferences = BackupPreferences(
            schedule = BackupSchedule.Daily,
            allowOnMobileData = false,
            lastAttemptAtEpochMs = 0L,
            lastSuccessAtEpochMs = 0L,
            lastError = null,
            autoBackupBootstrapped = false,
            aiExportEnabled = false,
        )
    }
}
