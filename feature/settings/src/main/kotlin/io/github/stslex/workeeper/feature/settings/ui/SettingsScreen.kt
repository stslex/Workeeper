// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedIconControl
import io.github.stslex.workeeper.core.ui.kit.components.segmented.SegmentedIcon
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.ui.components.BackupSection
import io.github.stslex.workeeper.feature.settings.ui.components.FrequencyPickerBottomSheet
import io.github.stslex.workeeper.feature.settings.ui.components.RestoreConfirmationDialog
import io.github.stslex.workeeper.feature.settings.ui.components.RestoreProgressOverlay
import io.github.stslex.workeeper.feature.settings.ui.components.RowChevron
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsBackupState
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsGroup
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsGroupRow
import io.github.stslex.workeeper.feature.settings.ui.components.SignOutConfirmationDialog
import kotlinx.collections.immutable.persistentListOf
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
            // §5.1: back + h1 «Настройки» — the default (20px → section) title, not h1.sm.
            AppTopBar(
                title = stringResource(R.string.feature_settings_title),
                navigation = {
                    AppIconButton(
                        modifier = Modifier.testTag("SettingsBackButton"),
                        icon = AppIcons.ChevronLeft,
                        contentDescription = stringResource(KitR.string.core_ui_kit_action_back),
                        onClick = { consume(Action.Navigation.Back) },
                    )
                },
            )
            // §5.2: a group is 32dp of air + a label — no container. Mockup order (§5.6):
            // Оформление → Резервные копии → Данные → О приложении. First group sits 8px
            // under the topbar (the mockup's inline margin-top override).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = AppDimension.Space.sm, bottom = AppDimension.Space.xl),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxl),
            ) {
                SettingsGroup(label = stringResource(R.string.feature_settings_section_appearance)) {
                    ThemeRow(
                        selected = state.themeMode,
                        onSelect = { mode -> consume(Action.Input.OnThemeChange(mode)) },
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
                SettingsGroup(label = stringResource(R.string.feature_settings_section_data)) {
                    SettingsGroupRow(
                        modifier = Modifier.testTag("SettingsArchiveRow"),
                        title = stringResource(R.string.feature_settings_archive_entry),
                        chevron = RowChevron.InApp,
                        onClick = { consume(Action.Click.OnArchiveClick) },
                    )
                }
                SettingsGroup(label = stringResource(R.string.feature_settings_section_about)) {
                    SettingsGroupRow(
                        title = stringResource(R.string.feature_settings_about_app_name),
                        subtitle = stringResource(
                            R.string.feature_settings_about_version_format,
                            state.appVersion,
                            state.appVersionCode,
                        ),
                    )
                    SettingsGroupRow(
                        title = stringResource(R.string.feature_settings_about_github),
                        chevron = RowChevron.External,
                        onClick = { consume(Action.Click.OnGitHubClick) },
                    )
                    SettingsGroupRow(
                        title = stringResource(R.string.feature_settings_about_license),
                        chevron = RowChevron.External,
                        onClick = { consume(Action.Click.OnLicenseClick) },
                    )
                    SettingsGroupRow(
                        title = stringResource(R.string.feature_settings_about_privacy),
                        chevron = RowChevron.External,
                        onClick = { consume(Action.Click.OnPrivacyPolicyClick) },
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

/**
 * §5.4/§5.6: the theme row is `.srow.plain` — the title, the CURRENT theme's name as the
 * sub-line, and the `.mseg` icon trio trailing. The mockup's own hint states the intent:
 * the theme became an ordinary row with a compact control, "не первый по важности элемент
 * экрана и не читается как вкладки". The old ThemeOption_* testTags ride on the mseg
 * buttons so the smoke suite keeps its handles.
 */
@Composable
private fun ThemeRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val modes = remember { listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK) }
    val names = modes.map { mode ->
        stringResource(
            when (mode) {
                ThemeMode.SYSTEM -> R.string.feature_settings_theme_system
                ThemeMode.LIGHT -> R.string.feature_settings_theme_light
                ThemeMode.DARK -> R.string.feature_settings_theme_dark
            },
        )
    }
    SettingsGroupRow(
        title = stringResource(R.string.feature_settings_theme_row_title),
        subtitle = names[modes.indexOf(selected)],
        trailing = {
            AppSegmentedIconControl(
                items = persistentListOf(
                    SegmentedIcon(AppIcons.ThemeSystem, names[0]),
                    SegmentedIcon(AppIcons.ThemeLight, names[1]),
                    SegmentedIcon(AppIcons.ThemeDark, names[2]),
                ),
                selected = modes.indexOf(selected),
                onSelectedChange = { index -> onSelect(modes[index]) },
                itemModifier = { index -> Modifier.testTag("ThemeOption_${modes[index].name}") },
            )
        },
    )
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
