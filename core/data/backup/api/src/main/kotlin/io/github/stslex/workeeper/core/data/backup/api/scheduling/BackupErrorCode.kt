package io.github.stslex.workeeper.core.data.backup.api.scheduling

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

/**
 * Flat enum mirror of [BackupError] variants, used for DataStore persistence of the
 * last backup attempt's failure mode. The sealed [BackupError] cannot be persisted
 * directly because two variants carry payloads (`CorruptedBackup.reason`,
 * `Io.cause`, `Unknown.cause`, `SchemaTooNew` versions) — the codes drop those
 * payloads and keep only the discriminator, which is all the auto-backup status
 * surface needs (banner / notification / settings badge).
 *
 * Persisted to DataStore via [Enum.name]; renaming or reordering variants requires
 * a migration. New [BackupError] variants must extend this enum and update
 * [from].
 */
enum class BackupErrorCode {
    NotAuthenticated,
    NetworkUnavailable,
    AuthRevoked,
    StorageQuotaExceeded,
    CorruptedBackup,
    SchemaTooNew,
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
            is BackupError.SchemaTooNew -> SchemaTooNew
            is BackupError.Io -> Io
            is BackupError.Unknown -> Unknown
        }
    }
}
