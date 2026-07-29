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
 * The `Резервные копии` group (extraction §5.6), in the `.srow` grammar — the
 * `AppButton.Secondary` pill rows die. Every row dispatches the exact Action.Backup its
 * predecessor did (the recovery flow's contract, verbatim); the `operation.isInProgress`
 * re-entrancy gate survives as click suppression + the per-operation trailing spinner
 * replacing the row's chevron.
 *
 * Rows the mockup does not draw but the code needs are kept in the same grammar and
 * reported: the signed-out sign-in row, the auth-paused banner, and the conditional
 * revert-last-restore row. The old separate `BackupInfoRow` block dies — its data is the
 * mockup's own sub-line on the restore row (`3 копии · последняя минуту назад`).
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
    info: BackupInfoUi?,
    preferences: BackupPreferencesUi?,
    canRevertLastRestore: Boolean,
    onAction: (Action.Backup) -> Unit,
) {
    // The account row — `.srow.plain`, the placeholder the mockup draws made real.
    SettingsGroupRow(
        title = auth.email,
        subtitle = auth.displayName?.takeIf { it.isNotBlank() },
    )
    if (preferences?.isAuthPaused == true) {
        AuthPausedBanner(onSignInClick = { onAction(Action.Backup.SignIn) })
    }
    // Hidden until the persisted preferences are observed — otherwise users whose schedule
    // != Daily would see a flash of the hard-coded default (the same anti-flash gate as
    // before the rebuild).
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
        // `.srow.plain` + `.sw` — the switch is the affordance, the row itself is inert
        // (the mockup's plain rows have no hover); the toggle grant may bounce through the
        // auth resolution launcher, so the in-flight spinner replaces the control.
        SettingsGroupRow(
            modifier = Modifier.testTag("AiExportRow"),
            title = stringResource(R.string.feature_settings_backup_ai_export_label),
            subtitle = stringResource(R.string.feature_settings_backup_ai_export_caption),
            trailing = {
                if (operation == BackupOperationUi.TogglingAiExport) {
                    RowSpinner()
                } else {
                    // Dispatch is deliberately UNGATED (pre-reskin behaviour): the handler
                    // honors ToggleAiExport(false) — consent withdrawal, which deletes the
                    // exported plaintext snapshots — unconditionally and before its own
                    // re-entrancy check; only the enable direction is gated, and there by
                    // the handler itself. A UI-side isInProgress gate would silently
                    // swallow a withdrawal while any backup operation is in flight.
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
        subtitle = info?.let {
            if (it.isEmpty) it.backupCountText else "${it.backupCountText} · ${it.lastBackupText}"
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
    // `.srow.rust` — destructive is text colour only, no chevron, no icon, no container.
    SettingsGroupRow(
        title = stringResource(R.string.feature_settings_backup_sign_out),
        destructive = true,
        onClick = {
            if (!operation.isInProgress) onAction(Action.Backup.RequestSignOut)
        },
        trailing = {
            if (operation == BackupOperationUi.SigningOut) RowSpinner()
        },
    )
}

/** A navigable action row: the chevron yields to the operation's spinner while in flight. */
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
        onClick = { if (enabled) onClick() },
        trailing = { if (loading) RowSpinner() },
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
                    email = "ilya977.077@gmail.com",
                    displayName = "Ilya Alexandrovich",
                ),
                operation = BackupOperationUi.Idle,
                info = BackupInfoUi(
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
                info = null,
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
