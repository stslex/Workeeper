// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_auth_revoked
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_backup_too_new
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_corrupted_backup
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_io
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_missing_migration_path
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_missing_required_scope
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_network_unavailable
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_no_backups_found
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_not_authenticated
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_storage_quota_exceeded
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_error_unknown
import org.jetbrains.compose.resources.StringResource
import io.github.stslex.workeeper.feature.settings.resources.Res as SettingsRes

enum class BackupErrorUi(val messageRes: StringResource) {
    NOT_AUTHENTICATED(SettingsRes.string.feature_settings_backup_error_not_authenticated),
    NETWORK_UNAVAILABLE(SettingsRes.string.feature_settings_backup_error_network_unavailable),
    AUTH_REVOKED(SettingsRes.string.feature_settings_backup_error_auth_revoked),
    MISSING_REQUIRED_SCOPE(SettingsRes.string.feature_settings_backup_error_missing_required_scope),
    STORAGE_QUOTA_EXCEEDED(SettingsRes.string.feature_settings_backup_error_storage_quota_exceeded),
    CORRUPTED_BACKUP(SettingsRes.string.feature_settings_backup_error_corrupted_backup),
    BACKUP_TOO_NEW(SettingsRes.string.feature_settings_backup_error_backup_too_new),
    MISSING_MIGRATION_PATH(SettingsRes.string.feature_settings_backup_error_missing_migration_path),
    IO_ERROR(SettingsRes.string.feature_settings_backup_error_io),
    UNKNOWN(SettingsRes.string.feature_settings_backup_error_unknown),
    NO_BACKUPS_FOUND(SettingsRes.string.feature_settings_backup_error_no_backups_found),
}
