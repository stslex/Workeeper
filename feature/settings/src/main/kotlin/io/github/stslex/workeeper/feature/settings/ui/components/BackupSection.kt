// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.switch.AppSwitch
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupPreferencesUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

/**
 * The backup settings group. `operation.isInProgress` shows as click suppression plus a
 * trailing spinner in place of the row's chevron.
 */
@Composable
internal fun BackupSection(
    state: SettingsBackupState,
    onAction: (Action.Backup) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(
        modifier = modifier,
        label = stringResource(R.string.feature_settings_backup_title),
    ) {
        when (state.auth) {
            BackupAuthUi.NotAuthenticated -> NotAuthenticatedRows(state.operation, onAction)
            is BackupAuthUi.Authenticated -> AuthenticatedRows(
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

@Composable
private fun NotAuthenticatedRows(
    operation: BackupOperationUi,
    onAction: (Action.Backup) -> Unit,
) {
    ActionRow(
        title = stringResource(R.string.feature_settings_backup_sign_in),
        loading = operation == BackupOperationUi.SigningIn,
        enabled = !operation.isInProgress,
        onClick = { onAction(Action.Backup.SignIn) },
    )
}

@Composable
private fun AuthenticatedRows(
    auth: BackupAuthUi.Authenticated,
    operation: BackupOperationUi,
    info: BackupInfoUi,
    preferences: BackupPreferencesUi?,
    canRevertLastRestore: Boolean,
    onAction: (Action.Backup) -> Unit,
) {
    SettingsGroupRow(
        title = auth.email,
        subtitle = auth.displayName?.takeIf { it.isNotBlank() },
    )
    if (preferences?.isAuthPaused == true) {
        AuthPausedBanner(onSignInClick = { onAction(Action.Backup.SignIn) })
    }
    // Hidden until the persisted preferences arrive, so a non-Daily schedule never flashes
    // the default.
    if (preferences != null) {
        val schedule = stringResource(preferences.schedule.labelRes())
        val subtitle = preferences.nextBackupText
            ?.let { next ->
                schedule + " · " + stringResource(
                    R.string.feature_settings_backup_auto_next_short,
                    next,
                )
            }
            ?: schedule
        SettingsGroupRow(
            modifier = Modifier.testTag("AutoBackupRow"),
            title = stringResource(R.string.feature_settings_backup_auto_row_title),
            subtitle = subtitle,
            value = stringResource(
                if (preferences.schedule == BackupScheduleUi.MANUAL_ONLY) {
                    R.string.feature_settings_backup_auto_off
                } else {
                    R.string.feature_settings_backup_auto_on
                },
            ),
            chevron = RowChevron.InApp,
            onClick = { onAction(Action.Backup.OpenFrequencyPicker) },
        )
        // The switch is the affordance, the row inert; the grant may bounce through the auth
        // resolution launcher, so the in-flight spinner replaces the control.
        SettingsGroupRow(
            modifier = Modifier.testTag("AiExportRow"),
            title = stringResource(R.string.feature_settings_backup_ai_export_label),
            subtitle = stringResource(R.string.feature_settings_backup_ai_export_caption),
            content = {
                if (operation == BackupOperationUi.TogglingAiExport) {
                    RowSpinner()
                } else {
                    // GUARD: never gate this dispatch on isInProgress — the handler gates
                    // only the enable direction, and a gate here swallows consent withdrawal.
                    AppSwitch(
                        checked = preferences.aiExportEnabled,
                        onCheckedChange = { enabled ->
                            onAction(Action.Backup.ToggleAiExport(enabled))
                        },
                    )
                }
            },
        )
    }
    ActionRow(
        title = stringResource(R.string.feature_settings_backup_create),
        loading = operation == BackupOperationUi.CreatingBackup,
        enabled = !operation.isInProgress,
        onClick = { onAction(Action.Backup.CreateBackup) },
    )
    ActionRow(
        title = stringResource(R.string.feature_settings_backup_restore),
        subtitle = when (info) {
            BackupInfoUi.Unknown -> null
            is BackupInfoUi.Empty -> info.backupCountText
            is BackupInfoUi.Present -> "${info.backupCountText} · ${info.lastBackupText}"
        },
        loading = operation == BackupOperationUi.FetchingBackups ||
            operation == BackupOperationUi.Restoring,
        enabled = !operation.isInProgress,
        onClick = { onAction(Action.Backup.RequestRestore) },
    )
    if (canRevertLastRestore) {
        ActionRow(
            title = stringResource(R.string.feature_settings_backup_revert_last_restore_label),
            loading = false,
            enabled = !operation.isInProgress,
            onClick = { onAction(Action.Backup.RequestRevertLastRestore) },
        )
    }
    SettingsGroupRow(
        title = stringResource(R.string.feature_settings_backup_sign_out),
        destructive = true,
        // null, not a swallowing wrapper: SettingsGroupRow keys its pressed flash on onClick.
        onClick = if (operation.isInProgress) {
            null
        } else {
            { onAction(Action.Backup.RequestSignOut) }
        },
        content = {
            if (operation == BackupOperationUi.SigningOut) RowSpinner()
        },
    )
}

/**
 * A navigable action row: the chevron yields to the operation's spinner while in flight.
 */
@Composable
private fun ActionRow(
    title: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    SettingsGroupRow(
        title = title,
        subtitle = subtitle,
        chevron = if (loading) RowChevron.None else RowChevron.InApp,
        onClick = onClick.takeIf { enabled },
        content = { if (loading) RowSpinner() },
    )
}

@Composable
private fun RowSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(AppDimension.iconSm),
        strokeWidth = AppDimension.Border.medium,
        color = AppUi.colors.textTertiary,
    )
}

@Preview
@Composable
private fun BackupSectionAuthenticatedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        BackupSection(
            state = SettingsBackupState(
                auth = BackupAuthUi.Authenticated(
                    email = "user@example.com",
                    displayName = "User",
                ),
                operation = BackupOperationUi.Idle,
                info = BackupInfoUi.Present(
                    lastBackupText = "последняя минуту назад",
                    backupCountText = "3 копии",
                ),
                preferences = BackupPreferencesUi(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = false,
                    nextBackupText = "через 23 ч",
                    isAuthPaused = false,
                    aiExportEnabled = true,
                ),
                canRevertLastRestore = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun BackupSectionNotAuthenticatedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        BackupSection(
            state = SettingsBackupState(
                auth = BackupAuthUi.NotAuthenticated,
                operation = BackupOperationUi.Idle,
                info = BackupInfoUi.Unknown,
                preferences = null,
                canRevertLastRestore = false,
            ),
            onAction = {},
        )
    }
}

private fun BackupScheduleUi.labelRes(): Int = when (this) {
    BackupScheduleUi.DAILY -> R.string.feature_settings_backup_frequency_daily
    BackupScheduleUi.WEEKLY -> R.string.feature_settings_backup_frequency_weekly
    BackupScheduleUi.MANUAL_ONLY -> R.string.feature_settings_backup_frequency_manual_only
}
