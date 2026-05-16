// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.error

/**
 * Typed failure surfaced by every fallible call on [io.github.stslex.workeeper.core.data.backup.api.BackupAuth]
 * and [io.github.stslex.workeeper.core.data.backup.api.BackupStorage]. Each variant
 * maps to a distinct user-facing state; impls translate provider-specific exceptions
 * into the closest variant and use [Unknown] only as a last resort.
 */
sealed interface BackupError {

    /** No signed-in account; caller must complete sign-in before retrying. */
    data object NotAuthenticated : BackupError

    /** Device has no usable network connection. Transient — caller may retry. */
    data object NetworkUnavailable : BackupError

    /**
     * Provider rejected the stored credential (token revoked, scope removed by the
     * user, account deleted). Caller must drive the user back through sign-in.
     */
    data object AuthRevoked : BackupError

    /**
     * User passed the consent screen but did not grant a hard-required scope (e.g.
     * `drive.appdata`). Terminal — distinct from [AuthRevoked] in that no token was
     * ever cached, and from [NotAuthenticated] in that a fresh sign-in attempt MUST
     * re-show the consent screen rather than silently reuse a partial grant.
     */
    data object MissingRequiredScope : BackupError

    /** Remote storage quota for this account is full. Non-retryable without user action. */
    data object StorageQuotaExceeded : BackupError

    /**
     * Backup payload is unusable — manifest could not be parsed or the db file did
     * not start with a valid SQLite header. [reason] is a free-form short label for
     * logging only and must not be surfaced to users verbatim.
     */
    data class CorruptedBackup(val reason: String) : BackupError

    /**
     * Backup was produced by an app version whose database schema is newer than the
     * one shipped with the installed app. Restoring would risk data loss, so the
     * caller must surface an "update the app to restore" prompt.
     */
    data class BackupTooNew(
        val backupSchemaVersion: Int,
        val appSchemaVersion: Int,
    ) : BackupError

    /**
     * Backup's schema version is ≤ the current code's schema, but the registered
     * migration graph has no path from `backupSchemaVersion` to `appSchemaVersion`.
     * Distinct from [BackupTooNew] (backup newer than code) and [CorruptedBackup]
     * (manifest unreadable / SQLite magic mismatch).
     */
    data class MissingMigrationPath(
        val backupSchemaVersion: Int,
        val appSchemaVersion: Int,
    ) : BackupError

    /**
     * Generic IO failure (disk full, file vanished, stream interrupted) that does
     * not match any more-specific variant.
     */
    data class Io(val cause: Throwable) : BackupError

    /** Last-resort variant for unclassified failures. Impls should prefer a typed variant. */
    data class Unknown(val cause: Throwable) : BackupError
}
