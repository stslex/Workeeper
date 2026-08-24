// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

/**
 * Flat enum mirror of [BackupError] for DataStore persistence of the last attempt's failure.
 * Persisted via [Enum.name] — renaming or reordering variants requires a migration.
 */
enum class BackupErrorCode {
    NotAuthenticated,
    NetworkUnavailable,
    AuthRevoked,
    StorageQuotaExceeded,
    CorruptedBackup,
    SchemaTooNew,
    MissingMigrationPath,
    Io,
    Unknown,
    MissingRequiredScope,
    ;

    companion object {

        fun from(error: BackupError): BackupErrorCode = when (error) {
            BackupError.NotAuthenticated -> NotAuthenticated
            BackupError.NetworkUnavailable -> NetworkUnavailable
            BackupError.AuthRevoked -> AuthRevoked
            BackupError.MissingRequiredScope -> MissingRequiredScope
            BackupError.StorageQuotaExceeded -> StorageQuotaExceeded
            is BackupError.CorruptedBackup -> CorruptedBackup
            is BackupError.BackupTooNew -> SchemaTooNew
            is BackupError.MissingMigrationPath -> MissingMigrationPath
            is BackupError.Io -> Io
            is BackupError.Unknown -> Unknown
        }
    }
}
