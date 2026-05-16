// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.ui.components.AboutBlock
import io.github.stslex.workeeper.feature.settings.ui.components.BackupSection
import io.github.stslex.workeeper.feature.settings.ui.components.FrequencyPickerBottomSheet
import io.github.stslex.workeeper.feature.settings.ui.components.RestoreConfirmationDialog
import io.github.stslex.workeeper.feature.settings.ui.components.RestoreProgressOverlay
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsBackupState
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsRow
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsSection
import io.github.stslex.workeeper.feature.settings.ui.components.SignOutConfirmationDialog
import io.github.stslex.workeeper.feature.settings.ui.components.ThemeSelector
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun SettingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppUi.colors.surfaceTier0)
                .testTag("SettingsScreen"),
        ) {
            AppTopAppBar(
                title = stringResource(R.string.feature_settings_title),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.testTag("SettingsBackButton"),
                        onClick = { consume(Action.Navigation.Back) },
                    ) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconMd),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(KitR.string.core_ui_kit_action_back),
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimension.sectionSpacing),
            ) {
                SettingsSection(title = stringResource(R.string.feature_settings_section_about)) {
                    AboutBlock(
                        appVersion = state.appVersion,
                        appVersionCode = state.appVersionCode,
                        onLicenseClick = { consume(Action.Click.OnLicenseClick) },
                        onGitHubClick = { consume(Action.Click.OnGitHubClick) },
                        onPrivacyClick = { consume(Action.Click.OnPrivacyPolicyClick) },
                    )
                }
                SettingsSection(title = stringResource(R.string.feature_settings_section_appearance)) {
                    ThemeSelector(
                        selected = state.themeMode,
                        onSelectedChange = { mode -> consume(Action.Input.OnThemeChange(mode)) },
                    )
                }
                BackupSection(
                    state = SettingsBackupState(
                        auth = state.backupAuth,
                        operation = state.backupOperation,
                        info = state.backupInfo,
                        preferences = state.backupPreferences,
                        canRevertLastRestore = state.canRevertLastRestore,
                    ),
                    onAction = { consume(it) },
                )
                SettingsSection(title = stringResource(R.string.feature_settings_section_data)) {
                    SettingsRow(
                        modifier = Modifier.testTag("SettingsArchiveRow"),
                        title = stringResource(R.string.feature_settings_archive_entry),
                        onClick = { consume(Action.Click.OnArchiveClick) },
                    )
                }
            }
        }
        when (val dialog = state.dialogState) {
            DialogState.Hidden -> Unit
            is DialogState.RestoreConfirmation -> RestoreConfirmationDialog(
                state = dialog,
                onAction = { consume(it) },
            )
            DialogState.SignOutConfirmation -> SignOutConfirmationDialog(
                onAction = { consume(it) },
            )
            is DialogState.FrequencyPicker -> FrequencyPickerBottomSheet(
                state = dialog,
                onAction = { consume(it) },
            )
        }
        RestoreProgressOverlay(state = state.restoreProgress)
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsScreenNotAuthenticatedPreview() {
    AppTheme {
        SettingsScreen(
            state = State.initial(appVersion = "1.0.0", appVersionCode = 15),
            consume = {},
        )
    }
}

@Preview(name = "Authenticated", showBackground = true)
@Composable
private fun SettingsScreenAuthenticatedPreview() {
    AppTheme {
        SettingsScreen(
            state = State.initial(appVersion = "1.0.0", appVersionCode = 15)
                .copy(
                    backupAuth = BackupAuthUi.Authenticated(
                        email = "user@example.com",
                        displayName = "User",
                    ),
                    backupInfo = BackupInfoUi(
                        lastBackupText = "Last backup: 2 hours ago",
                        backupCountText = "3 backups stored",
                    ),
                ),
            consume = {},
        )
    }
}

@Preview(name = "Restoring with dialog", showBackground = true)
@Composable
private fun SettingsScreenRestoreDialogPreview() {
    AppTheme {
        SettingsScreen(
            state = State.initial(appVersion = "1.0.0", appVersionCode = 15)
                .copy(
                    backupAuth = BackupAuthUi.Authenticated(
                        email = "user@example.com",
                        displayName = null,
                    ),
                    backupOperation = BackupOperationUi.Idle,
                    dialogState = DialogState.RestoreConfirmation(
                        createdAtFormatted = "May 8, 2026, 09:32",
                        sizeFormatted = "1.4 MB",
                    ),
                ),
            consume = {},
        )
    }
}

@Preview(name = "Sign-out confirmation", showBackground = true)
@Composable
private fun SettingsScreenSignOutConfirmationPreview() {
    AppTheme {
        SettingsScreen(
            state = State.initial(appVersion = "1.0.0", appVersionCode = 15)
                .copy(
                    backupAuth = BackupAuthUi.Authenticated(
                        email = "user@example.com",
                        displayName = "User",
                    ),
                    dialogState = DialogState.SignOutConfirmation,
                ),
            consume = {},
        )
    }
}

@Preview(name = "Restore in progress overlay", showBackground = true)
@Composable
private fun SettingsScreenRestoreInProgressPreview() {
    AppTheme {
        SettingsScreen(
            state = State.initial(appVersion = "1.0.0", appVersionCode = 15)
                .copy(
                    backupAuth = BackupAuthUi.Authenticated(
                        email = "user@example.com",
                        displayName = "User",
                    ),
                    restoreProgress = RestoreProgressUi.Restoring,
                ),
            consume = {},
        )
    }
}

@Preview(name = "Restore completed overlay", showBackground = true)
@Composable
private fun SettingsScreenRestoreCompletedPreview() {
    AppTheme {
        SettingsScreen(
            state = State.initial(appVersion = "1.0.0", appVersionCode = 15)
                .copy(
                    backupAuth = BackupAuthUi.Authenticated(
                        email = "user@example.com",
                        displayName = "User",
                    ),
                    restoreProgress = RestoreProgressUi.Completed,
                ),
            consume = {},
        )
    }
}
