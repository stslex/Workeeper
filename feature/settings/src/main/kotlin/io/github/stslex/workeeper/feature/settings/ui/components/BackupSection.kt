// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
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
                    canRevertLastRestore = state.canRevertLastRestore,
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
    canRevertLastRestore: Boolean,
    onAction: (Action.Backup) -> Unit,
) {
    AccountInfoRow(
        email = auth.email,
        displayName = auth.displayName,
    )
    if (preferences?.isAuthPaused == true) {
        AuthPausedBanner(onSignInClick = { onAction(Action.Backup.SignIn) })
    }
    // AutoBackupRow stays hidden until the persisted preferences are observed
    // — otherwise users whose schedule != Daily would see a brief flash of the
    // hard-coded default. The row will appear on the same recomposition that
    // ObservePreferences emits its first snapshot.
    if (preferences != null) {
        AutoBackupRow(
            schedule = preferences.schedule,
            nextBackupText = preferences.nextBackupText,
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
    if (canRevertLastRestore) {
        BackupButtonRow(
            title = stringResource(R.string.feature_settings_backup_revert_last_restore_label),
            onClick = { onAction(Action.Backup.RequestRevertLastRestore) },
            enabled = !operation.isInProgress,
            isLoading = false,
        )
    }
    BackupButtonRow(
        title = stringResource(R.string.feature_settings_backup_sign_out),
        onClick = { onAction(Action.Backup.RequestSignOut) },
        enabled = !operation.isInProgress,
        isLoading = operation == BackupOperationUi.SigningOut,
    )
    if (info != null) {
        BackupInfoRow(
            lastBackupText = info.lastBackupText,
            backupCountText = info.backupCountText,
        )
    }
}

@Preview
@Composable
private fun BackupSectionNotAuthenticatedPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        BackupSection(
            state = SettingsBackupState(
                auth = BackupAuthUi.NotAuthenticated,
                operation = BackupOperationUi.Idle,
                info = null,
                preferences = null,
                canRevertLastRestore = false,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun BackupSectionAuthenticatedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        BackupSection(
            state = SettingsBackupState(
                auth = BackupAuthUi.Authenticated(
                    email = "user@example.com",
                    displayName = "Sample User",
                ),
                operation = BackupOperationUi.Idle,
                info = BackupInfoUi(
                    lastBackupText = "Today, 09:42",
                    backupCountText = "12 backups",
                ),
                preferences = BackupPreferencesUi(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = false,
                    nextBackupText = "in 23 hours",
                    isAuthPaused = false,
                ),
                canRevertLastRestore = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun BackupSectionAuthenticatedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        BackupSection(
            state = SettingsBackupState(
                auth = BackupAuthUi.Authenticated(
                    email = "user@example.com",
                    displayName = "Sample User",
                ),
                operation = BackupOperationUi.Idle,
                info = BackupInfoUi(
                    lastBackupText = "Today, 09:42",
                    backupCountText = "12 backups",
                ),
                preferences = BackupPreferencesUi(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = false,
                    nextBackupText = "in 23 hours",
                    isAuthPaused = false,
                ),
                canRevertLastRestore = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun BackupSectionAuthPausedPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        BackupSection(
            state = SettingsBackupState(
                auth = BackupAuthUi.Authenticated(
                    email = "user@example.com",
                    displayName = "Sample User",
                ),
                operation = BackupOperationUi.Idle,
                info = BackupInfoUi(
                    lastBackupText = "Yesterday, 18:01",
                    backupCountText = "12 backups",
                ),
                preferences = BackupPreferencesUi(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = false,
                    nextBackupText = null,
                    isAuthPaused = true,
                ),
                canRevertLastRestore = false,
            ),
            onAction = {},
        )
    }
}
