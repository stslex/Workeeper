// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.content.Context
import android.text.format.Formatter
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import java.text.DateFormat
import java.util.Date

internal object BackupUiMapper {

    fun BackupAuthDomain.toUi(): BackupAuthUi = when (this) {
        BackupAuthDomain.NotAuthenticated -> BackupAuthUi.NotAuthenticated
        is BackupAuthDomain.Authenticated -> BackupAuthUi.Authenticated(
            email = account.email,
            displayName = account.displayName,
        )
    }

    fun BackupSummaryDomain.toConfirmation(context: Context): DialogState.RestoreConfirmation =
        DialogState.RestoreConfirmation(
            createdAtFormatted = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
            ).format(Date(createdAtEpochMs)),
            sizeFormatted = Formatter.formatShortFileSize(context, sizeBytes),
        )

    fun BackupError.toUi(): BackupErrorUi = when (this) {
        BackupError.NotAuthenticated -> BackupErrorUi.NOT_AUTHENTICATED
        BackupError.NetworkUnavailable -> BackupErrorUi.NETWORK_UNAVAILABLE
        BackupError.AuthRevoked -> BackupErrorUi.AUTH_REVOKED
        BackupError.MissingRequiredScope -> BackupErrorUi.MISSING_REQUIRED_SCOPE
        BackupError.StorageQuotaExceeded -> BackupErrorUi.STORAGE_QUOTA_EXCEEDED
        is BackupError.InsufficientLocalStorage,
        is BackupError.StorageCapacityUnavailable,
        -> BackupErrorUi.IO_ERROR
        is BackupError.CorruptedBackup -> BackupErrorUi.CORRUPTED_BACKUP
        is BackupError.BackupTooNew -> BackupErrorUi.BACKUP_TOO_NEW
        is BackupError.MissingMigrationPath -> BackupErrorUi.MISSING_MIGRATION_PATH
        is BackupError.Io -> BackupErrorUi.IO_ERROR
        is BackupError.Unknown -> BackupErrorUi.UNKNOWN
    }
}
