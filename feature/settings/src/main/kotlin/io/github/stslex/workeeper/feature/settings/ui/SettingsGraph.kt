// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.di.SettingsFeature
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event

fun NavGraphBuilder.settingsGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(SettingsFeature) { processor ->
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val backupCreatedMessage = stringResource(R.string.feature_settings_backup_created)
        val autoBackupEnabledMessage =
            stringResource(R.string.feature_settings_backup_auto_enabled_default)
        val autoBackupEnabledAction =
            stringResource(R.string.feature_settings_backup_auto_enabled_change_action)
        val backupErrorMessages = backupErrorMessages()

        val authResolutionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            processor.consume(Action.Backup.HandleAuthResult(result.data))
        }

        processor.Handle { event ->
            when (event) {
                is Event.ShowExternalLink -> {
                    val intent = Intent(Intent.ACTION_VIEW, event.url.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }

                is Event.Haptic -> haptic.performHapticFeedback(event.type)

                is Event.AuthResolutionRequested -> {
                    authResolutionLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build(),
                    )
                }

                is Event.ShowBackupError -> {
                    SnackbarManager.showSnackbar(backupErrorMessages.getValue(event.error))
                }

                Event.ShowBackupCreated -> SnackbarManager.showSnackbar(backupCreatedMessage)

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
    val notAuthenticated = stringResource(R.string.feature_settings_backup_error_not_authenticated)
    val network = stringResource(R.string.feature_settings_backup_error_network_unavailable)
    val authRevoked = stringResource(R.string.feature_settings_backup_error_auth_revoked)
    val quota = stringResource(R.string.feature_settings_backup_error_storage_quota_exceeded)
    val corrupted = stringResource(R.string.feature_settings_backup_error_corrupted_backup)
    val backupTooNew = stringResource(R.string.feature_settings_backup_error_backup_too_new)
    val missingMigrationPath =
        stringResource(R.string.feature_settings_backup_error_missing_migration_path)
    val io = stringResource(R.string.feature_settings_backup_error_io)
    val unknown = stringResource(R.string.feature_settings_backup_error_unknown)
    val noBackups = stringResource(R.string.feature_settings_backup_error_no_backups_found)
    return remember(
        notAuthenticated, network, authRevoked, quota, corrupted,
        backupTooNew, missingMigrationPath, io, unknown, noBackups,
    ) {
        mapOf(
            BackupErrorUi.NOT_AUTHENTICATED to notAuthenticated,
            BackupErrorUi.NETWORK_UNAVAILABLE to network,
            BackupErrorUi.AUTH_REVOKED to authRevoked,
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
