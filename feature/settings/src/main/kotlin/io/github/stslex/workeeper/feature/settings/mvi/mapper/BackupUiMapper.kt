// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.content.Context
import android.text.format.Formatter
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreConfirmationUi
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

    fun BackupSummaryDomain.toConfirmation(context: Context): RestoreConfirmationUi =
        RestoreConfirmationUi(
            createdAtFormatted = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
            ).format(Date(createdAtEpochMs)),
            sizeFormatted = Formatter.formatShortFileSize(context, sizeBytes),
            appVersion = appVersion,
        )

    fun BackupError.toUi(): BackupErrorUi = when (this) {
        BackupError.NotAuthenticated -> BackupErrorUi.NOT_AUTHENTICATED
        BackupError.NetworkUnavailable -> BackupErrorUi.NETWORK_UNAVAILABLE
        BackupError.AuthRevoked -> BackupErrorUi.AUTH_REVOKED
        BackupError.StorageQuotaExceeded -> BackupErrorUi.STORAGE_QUOTA_EXCEEDED
        is BackupError.CorruptedBackup -> BackupErrorUi.CORRUPTED_BACKUP
        is BackupError.SchemaTooNew -> BackupErrorUi.SCHEMA_TOO_NEW
        is BackupError.Io -> BackupErrorUi.IO_ERROR
        is BackupError.Unknown -> BackupErrorUi.UNKNOWN
    }
}
