// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.settings.di.SettingsFeature
import io.github.stslex.workeeper.feature.settings.di.SettingsGraph
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_ai_export_access_needed
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_auto_enabled_change_action
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_auto_enabled_default
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_created
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
import io.github.stslex.workeeper.feature.settings.ui.platform.rememberSettingsAuthLauncher
import io.github.stslex.workeeper.feature.settings.ui.platform.rememberSettingsLinkLauncher
import org.jetbrains.compose.resources.stringResource
import io.github.stslex.workeeper.feature.settings.resources.Res as SettingsRes

fun NavGraphScope.settingsGraph(
    factory: SettingsGraph.Factory,
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.Settings> {
        val feature = remember(factory) { SettingsFeature(factory) }
        val processor = feature.processor()
        val linkLauncher = rememberSettingsLinkLauncher()
        val haptic = LocalHapticFeedback.current
        val backupCreatedMessage = stringResource(SettingsRes.string.feature_settings_backup_created)
        val aiExportAccessNeededMessage =
            stringResource(SettingsRes.string.feature_settings_backup_ai_export_access_needed)
        val autoBackupEnabledMessage =
            stringResource(SettingsRes.string.feature_settings_backup_auto_enabled_default)
        val autoBackupEnabledAction =
            stringResource(SettingsRes.string.feature_settings_backup_auto_enabled_change_action)
        val backupErrorMessages = backupErrorMessages()

        val authResolutionLauncher = rememberSettingsAuthLauncher { result ->
            processor.consume(Action.Backup.HandleAuthResult(result))
        }

        processor.Handle { event ->
            when (event) {
                is Event.ShowExternalLink -> linkLauncher.launch(event.url)

                is Event.Haptic -> haptic.performHapticFeedback(event.type)

                is Event.AuthResolutionRequested -> authResolutionLauncher.launch(event.request)

                is Event.ShowBackupError -> {
                    val message = backupErrorMessages[event.error]
                        ?: backupErrorMessages.getValue(BackupErrorUi.UNKNOWN)
                    SnackbarManager.showSnackbar(message)
                }

                Event.ShowBackupCreated -> SnackbarManager.showSnackbar(backupCreatedMessage)

                Event.ShowAiExportAccessNeeded ->
                    SnackbarManager.showSnackbar(aiExportAccessNeededMessage)

                Event.ShowAutoBackupEnabledSnackbarRequested -> SnackbarManager.showSnackbar(
                    message = autoBackupEnabledMessage,
                    actionLabel = autoBackupEnabledAction,
                    action = { processor.consume(Action.Backup.OpenFrequencyPicker) },
                )
            }
        }

        SettingsScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}

@androidx.compose.runtime.Composable
private fun backupErrorMessages(): Map<BackupErrorUi, String> {
    val notAuthenticated = stringResource(SettingsRes.string.feature_settings_backup_error_not_authenticated)
    val network = stringResource(SettingsRes.string.feature_settings_backup_error_network_unavailable)
    val authRevoked = stringResource(SettingsRes.string.feature_settings_backup_error_auth_revoked)
    val missingRequiredScope =
        stringResource(SettingsRes.string.feature_settings_backup_error_missing_required_scope)
    val quota = stringResource(SettingsRes.string.feature_settings_backup_error_storage_quota_exceeded)
    val corrupted = stringResource(SettingsRes.string.feature_settings_backup_error_corrupted_backup)
    val backupTooNew = stringResource(SettingsRes.string.feature_settings_backup_error_backup_too_new)
    val missingMigrationPath =
        stringResource(SettingsRes.string.feature_settings_backup_error_missing_migration_path)
    val io = stringResource(SettingsRes.string.feature_settings_backup_error_io)
    val unknown = stringResource(SettingsRes.string.feature_settings_backup_error_unknown)
    val noBackups = stringResource(SettingsRes.string.feature_settings_backup_error_no_backups_found)
    return remember(
        notAuthenticated, network, authRevoked, missingRequiredScope, quota, corrupted,
        backupTooNew, missingMigrationPath, io, unknown, noBackups,
    ) {
        mapOf(
            BackupErrorUi.NOT_AUTHENTICATED to notAuthenticated,
            BackupErrorUi.NETWORK_UNAVAILABLE to network,
            BackupErrorUi.AUTH_REVOKED to authRevoked,
            BackupErrorUi.MISSING_REQUIRED_SCOPE to missingRequiredScope,
            BackupErrorUi.STORAGE_QUOTA_EXCEEDED to quota,
            BackupErrorUi.CORRUPTED_BACKUP to corrupted,
            BackupErrorUi.BACKUP_TOO_NEW to backupTooNew,
            BackupErrorUi.MISSING_MIGRATION_PATH to missingMigrationPath,
            BackupErrorUi.IO_ERROR to io,
            BackupErrorUi.UNKNOWN to unknown,
            BackupErrorUi.NO_BACKUPS_FOUND to noBackups,
        )
    }
}
