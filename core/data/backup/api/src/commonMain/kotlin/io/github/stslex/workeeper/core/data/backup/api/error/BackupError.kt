// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.error

/** Typed failure surfaced by every fallible backup call; [Unknown] is the last resort. */
sealed interface BackupError {

    /** No signed-in account; caller must complete sign-in before retrying. */
    data object NotAuthenticated : BackupError

    /** Device has no usable network connection. Transient — caller may retry. */
    data object NetworkUnavailable : BackupError

    /** Provider rejected the stored credential; caller must drive the user back to sign-in. */
    data object AuthRevoked : BackupError

    /** Consent passed without a hard-required scope; a fresh sign-in MUST re-show consent. */
    data object MissingRequiredScope : BackupError

    /** Remote storage quota for this account is full. Non-retryable without user action. */
    data object StorageQuotaExceeded : BackupError

    /** Backup payload is unusable; [reason] is a log-only label, never shown to users. */
    data class CorruptedBackup(val reason: String) : BackupError

    /** Backup's schema is newer than the installed app's; prompt to update, do not restore. */
    data class BackupTooNew(
        val backupSchemaVersion: Int,
        val appSchemaVersion: Int,
    ) : BackupError

    /** Backup schema is not newer, but the migration graph has no path from it to the app's. */
    data class MissingMigrationPath(
        val backupSchemaVersion: Int,
        val appSchemaVersion: Int,
    ) : BackupError

    /** Generic IO failure that does not match any more-specific variant. */
    data class Io(val cause: Throwable) : BackupError

    /** Last-resort variant for unclassified failures. Impls should prefer a typed variant. */
    data class Unknown(val cause: Throwable) : BackupError
}
