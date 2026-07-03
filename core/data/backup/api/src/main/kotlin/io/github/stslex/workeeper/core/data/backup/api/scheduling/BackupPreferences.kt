// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

/**
 * Snapshot of the persisted auto-backup settings and last-attempt status. Single
 * source of truth for the auto-backup banner / "Next backup" line / paused state
 * — both the worker and the settings UI read from [BackupPreferencesRepository]
 * and derive their display from this shape.
 *
 * [lastError] is `null` when the last attempt succeeded or there has been no
 * attempt yet ([lastAttemptAtEpochMs] == 0L); a non-null value means the latest
 * attempt failed and has not yet been superseded by a success.
 *
 * [autoBackupBootstrapped] flips to true the first time a sign-in success path
 * has scheduled the initial Daily periodic work + the one-shot immediate
 * backup. The flag is load-bearing for the first-sign-in flow — once set, the
 * "Auto-backup enabled, daily" snackbar must NOT be emitted again on
 * subsequent sign-ins.
 */
data class BackupPreferences(
    val schedule: BackupSchedule,
    val allowOnMobileData: Boolean,
    val lastAttemptAtEpochMs: Long,
    val lastSuccessAtEpochMs: Long,
    val lastError: BackupErrorCode?,
    val autoBackupBootstrapped: Boolean,
    /**
     * Whether the user enabled the AI-readable JSON snapshot export. Off by default and
     * flipped on only via the Settings toggle after the `drive.file` grant is confirmed
     * (pessimistic / opt-in). The export runner gates on this AND the actual `drive.file`
     * grant, so it stays inert until both hold.
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
