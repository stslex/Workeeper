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
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
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
                is BackupAuthUi.Authenticated -> AuthenticatedBlock(state.auth, state.operation, onAction)
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
    onAction: (Action.Backup) -> Unit,
) {
    AccountInfoRow(email = auth.email, displayName = auth.displayName)
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
        onClick = { onAction(Action.Backup.SignOut) },
        enabled = !operation.isInProgress,
        isLoading = operation == BackupOperationUi.SigningOut,
    )
}
