// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthRequest
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthResult
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.ui.platform.SettingsAuthLauncher
import io.github.stslex.workeeper.feature.settings.ui.platform.rememberSettingsAuthLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class SettingsUnavailableBackupTest {
    private var wasLogging = true

    @BeforeTest
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
    }

    @AfterTest
    fun tearDown() {
        Log.isLogging = wasLogging
    }

    @Test
    fun productionGraphConstructsSettingsWithUnavailableBackup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = SettingsNativeGraphFixture(dispatcher)
        try {
            fixture.store.init(fixture.owner)
            runCurrent()
            assertFalse(fixture.store.state.value.backupAvailable)
            assertEquals(0, fixture.store.state.value.archivedCounts?.exercises)
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun allBackupActionsLeaveStateAndEventsUnchanged() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = SettingsNativeGraphFixture(dispatcher)
        try {
            fixture.store.init(fixture.owner)
            runCurrent()
            val initial = fixture.store.state.value
            assertFalse(initial.backupAvailable)
            val events = mutableListOf<Event>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.store.event.collect { events += it }
            }
            val actions = backupActions()
            assertEquals(19, actions.size)
            assertEquals(19, actions.map(::backupActionName).toSet().size)
            actions.forEach { action ->
                fixture.store.consume(action)
                runCurrent()
                assertEquals(initial, fixture.store.state.value, backupActionName(action))
                assertEquals(emptyList<Event>(), events, backupActionName(action))
            }
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unavailableAuthLauncherNeverCallsBack() = runComposeUiTest {
        var callbacks = 0
        lateinit var launcher: SettingsAuthLauncher
        setContent { launcher = rememberSettingsAuthLauncher { callbacks++ } }
        waitForIdle()
        runOnIdle { launcher.launch(object : SettingsAuthRequest {}) }
        waitForIdle()
        assertEquals(0, callbacks)
    }

    private fun backupActions(): List<Action.Backup> = listOf(
        Action.Backup.ObserveAuth,
        Action.Backup.ObservePreferences,
        Action.Backup.ObserveRestoreState,
        Action.Backup.RequestRevertLastRestore,
        Action.Backup.SignIn,
        Action.Backup.HandleAuthResult(object : SettingsAuthResult {}),
        Action.Backup.RequestSignOut,
        Action.Backup.ConfirmSignOut,
        Action.Backup.DismissSignOutConfirmation,
        Action.Backup.CreateBackup,
        Action.Backup.RequestRestore,
        Action.Backup.ConfirmRestore,
        Action.Backup.DismissRestoreDialog,
        Action.Backup.LoadBackupList,
        Action.Backup.OpenFrequencyPicker,
        Action.Backup.DismissFrequencyPicker,
        Action.Backup.SaveFrequency(BackupScheduleUi.DAILY, true),
        Action.Backup.UpdateFrequencyPickerSelection(BackupScheduleUi.WEEKLY, false),
        Action.Backup.ToggleAiExport(true),
    )

    // Exhaustive: a new sealed variant requires a deliberate sample as well.
    private fun backupActionName(action: Action.Backup): String = when (action) {
        Action.Backup.ObserveAuth -> "ObserveAuth"
        Action.Backup.ObservePreferences -> "ObservePreferences"
        Action.Backup.ObserveRestoreState -> "ObserveRestoreState"
        Action.Backup.RequestRevertLastRestore -> "RequestRevertLastRestore"
        Action.Backup.SignIn -> "SignIn"
        is Action.Backup.HandleAuthResult -> "HandleAuthResult"
        Action.Backup.RequestSignOut -> "RequestSignOut"
        Action.Backup.ConfirmSignOut -> "ConfirmSignOut"
        Action.Backup.DismissSignOutConfirmation -> "DismissSignOutConfirmation"
        Action.Backup.CreateBackup -> "CreateBackup"
        Action.Backup.RequestRestore -> "RequestRestore"
        Action.Backup.ConfirmRestore -> "ConfirmRestore"
        Action.Backup.DismissRestoreDialog -> "DismissRestoreDialog"
        Action.Backup.LoadBackupList -> "LoadBackupList"
        Action.Backup.OpenFrequencyPicker -> "OpenFrequencyPicker"
        Action.Backup.DismissFrequencyPicker -> "DismissFrequencyPicker"
        is Action.Backup.SaveFrequency -> "SaveFrequency"
        is Action.Backup.UpdateFrequencyPickerSelection -> "UpdateFrequencyPickerSelection"
        is Action.Backup.ToggleAiExport -> "ToggleAiExport"
    }
}
