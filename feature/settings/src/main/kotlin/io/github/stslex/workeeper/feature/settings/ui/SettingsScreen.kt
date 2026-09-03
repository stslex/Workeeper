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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedIconControl
import io.github.stslex.workeeper.core.ui.kit.components.segmented.SegmentedIcon
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_back
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.StartCardModeSheet
import io.github.stslex.workeeper.core.ui.start_mode.startCardModeName
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
import org.jetbrains.compose.resources.stringResource

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
            AppTopBar(
                title = stringResource(R.string.feature_settings_title),
                navigation = {
                    AppIconButton(
                        modifier = Modifier.testTag("SettingsBackButton"),
                        icon = AppIcons.ChevronLeft,
                        contentDescription = stringResource(Res.string.core_ui_kit_action_back),
                        onClick = { consume(Action.Navigation.Back) },
                    )
                },
            )
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
                    // The start card's second entry point; it configures a surface, not data.
                    SettingsGroupRow(
                        modifier = Modifier.testTag("SettingsStartCardModeRow"),
                        title = stringResource(R.string.feature_settings_start_card_row_title),
                        // Null until the preference's first emission — no guessed default.
                        subtitle = state.startCardMode?.let { mode -> startCardModeName(mode) },
                        chevron = RowChevron.InApp,
                        onClick = { consume(Action.Click.OnStartCardModeClick) },
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
                        // Null until the counts arrive: an empty row is honest, a "0 · 0" is not.
                        subtitle = state.archivedCounts?.let { counts ->
                            listOf(
                                pluralStringResource(
                                    R.plurals.feature_settings_archive_exercise_count,
                                    counts.exercises,
                                    counts.exercises,
                                ),
                                pluralStringResource(
                                    R.plurals.feature_settings_archive_training_count,
                                    counts.trainings,
                                    counts.trainings,
                                ),
                            ).joinToString(" · ")
                        },
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
            // GUARD: pass the null through, never a default — until the preference's first
            // emission the sheet must check nothing. Pinned by SettingsStartCardModeSheetTest.
            DialogState.StartCardModePicker -> StartCardModeSheet(
                selected = state.startCardMode,
                onSelect = { mode -> consume(Action.Input.OnStartCardModeChange(mode)) },
                onDismiss = { consume(Action.Click.OnStartCardModeSheetDismiss) },
            )
        }
        RestoreProgressOverlay(state = state.restoreProgress)
    }
}

/**
 * The theme row: title, the current theme's name as the sub-line, and the icon trio trailing.
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
        content = {
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
                    backupInfo = BackupInfoUi.Present(
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
