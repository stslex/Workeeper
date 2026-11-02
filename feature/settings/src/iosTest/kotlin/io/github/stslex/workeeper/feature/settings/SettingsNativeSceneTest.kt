// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.about.AboutLinks
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupPreferencesUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.resources.Res
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_count
import io.github.stslex.workeeper.feature.settings.ui.SettingsScreen
import io.github.stslex.workeeper.feature.settings.ui.platform.SettingsLinkLauncher
import io.github.stslex.workeeper.feature.settings.ui.platform.rememberSettingsLinkLauncher
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SettingsNativeSceneTest {
    @Test
    fun settingsWithoutBackupKeepsGeneralActions() = runComposeUiTest {
        val state = mutableStateOf(initial())
        val actions = mutableListOf<Action>()
        val urls = mutableListOf<String>()
        lateinit var links: SettingsLinkLauncher
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { urls += uri }
        }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                links = rememberSettingsLinkLauncher()
                AppTheme { SettingsScreen(state.value, actions::add) }
            }
        }
        waitForIdle()
        onNodeWithTag("SettingsScreen").assertIsDisplayed()
        onNodeWithTag("SettingsBackupSection").assertDoesNotExist()
        onNodeWithText("Sign in to Google Drive").assertDoesNotExist()
        onNodeWithTag("ThemeOption_DARK").performClick()
        onNodeWithTag("SettingsArchiveRow").performScrollTo().performClick()
        onNodeWithTag("SettingsStartCardModeRow").performScrollTo().performClick()
        runOnIdle { state.value = state.value.copy(dialogState = DialogState.StartCardModePicker) }
        waitForIdle()
        onNodeWithTag("StartCardModeRow_LAGGING_GROUPS").performClick()
        runOnIdle { state.value = state.value.copy(dialogState = DialogState.Hidden) }
        waitForIdle()
        onNodeWithText("Source code").performScrollTo().performClick()
        onNodeWithTag("SettingsBackButton").performClick()
        runOnIdle { links.launch(AboutLinks.GITHUB_URL) }
        assertEquals(
            listOf(
                Action.Input.OnThemeChange(ThemeMode.DARK),
                Action.Click.OnArchiveClick,
                Action.Click.OnStartCardModeClick,
                Action.Input.OnStartCardModeChange(StartCardModeUi.LAGGING_GROUPS),
                Action.Click.OnGitHubClick,
                Action.Navigation.Back,
            ),
            actions,
        )
        assertEquals(listOf(AboutLinks.GITHUB_URL), urls)
    }

    @Test
    fun unavailableBackupHidesSectionDialogsAndOverlay() = runComposeUiTest {
        val state = mutableStateOf(enabledFixture().copy(backupAvailable = false))
        setContent { AppTheme { SettingsScreen(state.value, {}) } }
        val dialogs = listOf(
            DialogState.Hidden,
            DialogState.RestoreConfirmation("May 8, 2026", "1.4 MB"),
            DialogState.SignOutConfirmation,
            DialogState.FrequencyPicker(BackupScheduleUi.WEEKLY, true),
        )
        val overlays = listOf(RestoreProgressUi.Idle, RestoreProgressUi.Restoring, RestoreProgressUi.Completed)
        dialogs.forEach { dialog ->
            overlays.forEach { overlay ->
                runOnIdle { state.value = state.value.copy(dialogState = dialog, restoreProgress = overlay) }
                waitForIdle()
                onNodeWithTag("SettingsBackupSection").assertDoesNotExist()
                onNodeWithText("Sign in to Google Drive").assertDoesNotExist()
                onNodeWithTag("SettingsRestoreDialog").assertDoesNotExist()
                onNodeWithTag("SettingsSignOutDialog").assertDoesNotExist()
                onNodeWithTag("FrequencyPickerBottomSheet").assertDoesNotExist()
                onNodeWithTag("SettingsRestoreProgress").assertDoesNotExist()
            }
        }
        runOnIdle { state.value = initial().copy(dialogState = DialogState.StartCardModePicker) }
        waitForIdle()
        onNodeWithTag("StartCardModeRow_WEEK").assertExists()
    }

    @Test
    fun startCardModeWaitsForThePersistedPreference() = runComposeUiTest {
        val state = mutableStateOf(initial().copy(dialogState = DialogState.StartCardModePicker))
        setContent { AppTheme { SettingsScreen(state.value, {}) } }
        waitForIdle()
        StartCardModeUi.entries.forEach { mode ->
            onNodeWithTag("StartCardModeCheck_${mode.name}", useUnmergedTree = true).assertDoesNotExist()
        }
        runOnIdle { state.value = state.value.copy(startCardMode = StartCardModeUi.LAGGING_GROUPS) }
        waitForIdle()
        onNodeWithTag("StartCardModeCheck_LAGGING_GROUPS", useUnmergedTree = true).assertExists()
        StartCardModeUi.entries.filterNot { it == StartCardModeUi.LAGGING_GROUPS }.forEach { mode ->
            onNodeWithTag("StartCardModeCheck_${mode.name}", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun sharedBackupContentRendersWithAnEnabledFixture() = runComposeUiTest {
        val resolver = ComposeStringResolver()
        runBlocking {
            assertEquals("1 backup", resolver.getPluralString(Res.plurals.feature_settings_backup_info_count, 1, 1))
            assertEquals("2 backups", resolver.getPluralString(Res.plurals.feature_settings_backup_info_count, 2, 2))
            assertEquals("5 backups", resolver.getPluralString(Res.plurals.feature_settings_backup_info_count, 5, 5))
            BackupErrorUi.entries.forEach { assertTrue(resolver.getString(it.messageRes).isNotEmpty()) }
        }
        val state = mutableStateOf(initial().copy(backupAvailable = true))
        setContent { AppTheme { SettingsScreen(state.value, {}) } }
        waitForIdle()
        onNodeWithTag("SettingsBackupSection").assertExists()
        onNodeWithText("Sign in to Google Drive").assertExists()
        runOnIdle { state.value = enabledFixture() }
        waitForIdle()
        onNodeWithText("Create backup now").assertExists()
        onNodeWithText("Revert last restore").assertExists()
        runOnIdle {
            state.value = enabledFixture().copy(
                backupPreferences = enabledFixture().backupPreferences?.copy(isAuthPaused = true),
            )
        }
        waitForIdle()
        onNodeWithTag("AuthPausedBanner").assertExists()
        runOnIdle {
            state.value = enabledFixture().copy(
                dialogState = DialogState.RestoreConfirmation("May 8, 2026", "1.4 MB"),
            )
        }
        waitForIdle()
        onNodeWithTag("SettingsRestoreDialog").assertExists()
        runOnIdle { state.value = enabledFixture().copy(dialogState = DialogState.SignOutConfirmation) }
        waitForIdle()
        onNodeWithTag("SettingsSignOutDialog").assertExists()
        runOnIdle {
            state.value = enabledFixture().copy(
                dialogState = DialogState.FrequencyPicker(BackupScheduleUi.DAILY, false),
            )
        }
        waitForIdle()
        onNodeWithTag("FrequencyPickerBottomSheet").assertExists()
        listOf(RestoreProgressUi.Restoring, RestoreProgressUi.Completed).forEach { overlay ->
            runOnIdle { state.value = enabledFixture().copy(restoreProgress = overlay) }
            waitForIdle()
            onNodeWithTag("SettingsRestoreProgress").assertIsDisplayed()
        }
    }

    private fun initial(): State = State.initial(appVersion = "1.0.0", appVersionCode = 1, backupAvailable = false)

    private fun enabledFixture(): State = initial().copy(
        backupAvailable = true,
        backupAuth = BackupAuthUi.Authenticated("user@example.com", "User"),
        backupInfo = BackupInfoUi.Present("Last backup: 2 hours ago", "3 backups stored"),
        backupPreferences = BackupPreferencesUi(BackupScheduleUi.DAILY, false, "in 23 h", false, true),
        canRevertLastRestore = true,
    )
}
