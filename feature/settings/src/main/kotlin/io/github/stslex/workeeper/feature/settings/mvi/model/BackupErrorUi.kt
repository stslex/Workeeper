// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.settings.R

internal enum class BackupErrorUi(@StringRes val messageRes: Int) {
    NOT_AUTHENTICATED(R.string.feature_settings_backup_error_not_authenticated),
    NETWORK_UNAVAILABLE(R.string.feature_settings_backup_error_network_unavailable),
    AUTH_REVOKED(R.string.feature_settings_backup_error_auth_revoked),
    STORAGE_QUOTA_EXCEEDED(R.string.feature_settings_backup_error_storage_quota_exceeded),
    CORRUPTED_BACKUP(R.string.feature_settings_backup_error_corrupted_backup),
    SCHEMA_TOO_NEW(R.string.feature_settings_backup_error_schema_too_new),
    IO_ERROR(R.string.feature_settings_backup_error_io),
    UNKNOWN(R.string.feature_settings_backup_error_unknown),
    NO_BACKUPS_FOUND(R.string.feature_settings_backup_error_no_backups_found),
}
