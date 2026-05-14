// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupPreferencesUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

@Composable
internal fun BackupSection(
    state: SettingsBackupState,
    onAction: (Action.Backup) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        modifier = modifier,
        title = stringResource(R.string.feature_settings_backup_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            when (state.auth) {
                BackupAuthUi.NotAuthenticated -> NotAuthenticatedBlock(state.operation, onAction)
                is BackupAuthUi.Authenticated -> AuthenticatedBlock(
                    auth = state.auth,
                    operation = state.operation,
                    info = state.info,
                    preferences = state.preferences,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun NotAuthenticatedBlock(
    operation: BackupOperationUi,
    onAction: (Action.Backup) -> Unit,
) {
    BackupButtonRow(
        title = stringResource(R.string.feature_settings_backup_sign_in),
        onClick = { onAction(Action.Backup.SignIn) },
        enabled = !operation.isInProgress,
        isLoading = operation == BackupOperationUi.SigningIn,
    )
}

@Composable
private fun AuthenticatedBlock(
    auth: BackupAuthUi.Authenticated,
    operation: BackupOperationUi,
    info: BackupInfoUi?,
    preferences: BackupPreferencesUi?,
    onAction: (Action.Backup) -> Unit,
) {
    AccountInfoRow(email = auth.email, displayName = auth.displayName)
    if (preferences?.isAuthPaused == true) {
        AuthPausedBanner(onSignInClick = { onAction(Action.Backup.SignIn) })
    }
    if (info != null) {
        BackupInfoRow(
            lastBackupText = info.lastBackupText,
            backupCountText = info.backupCountText,
        )
    }
    if (preferences != null) {
        AutoBackupRow(
            schedule = preferences.schedule,
            nextBackupText = preferences.nextBackupText,
            onClick = { onAction(Action.Backup.OpenFrequencyPicker) },
        )
    } else {
        AutoBackupRow(
            schedule = BackupScheduleUi.WEEKLY,
            nextBackupText = null,
            onClick = { onAction(Action.Backup.OpenFrequencyPicker) },
        )
    }
    BackupButtonRow(
        title = stringResource(R.string.feature_settings_backup_create),
        onClick = { onAction(Action.Backup.CreateBackup) },
        enabled = !operation.isInProgress,
        isLoading = operation == BackupOperationUi.CreatingBackup,
    )
    BackupButtonRow(
        title = stringResource(R.string.feature_settings_backup_restore),
        onClick = { onAction(Action.Backup.RequestRestore) },
        enabled = !operation.isInProgress,
        isLoading = operation == BackupOperationUi.FetchingBackups ||
            operation == BackupOperationUi.Restoring,
    )
    BackupButtonRow(
        title = stringResource(R.string.feature_settings_backup_sign_out),
        onClick = { onAction(Action.Backup.RequestSignOut) },
        enabled = !operation.isInProgress,
        isLoading = operation == BackupOperationUi.SigningOut,
    )
}
