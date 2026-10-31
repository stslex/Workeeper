// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsStoreFixture
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsBackupHandler
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SettingsBackupAvailabilityTest {

    private var wasLogging = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun availableBackupInitializesAllThreeObservers() = runTest {
        val actions = mutableListOf<Action.Backup>()
        val handler = object : SettingsBackupHandler {
            override val isAvailable = true
            override fun invoke(action: Action.Backup) { actions += action }
        }
        val fixture = SettingsStoreFixture(StandardTestDispatcher(testScheduler), handler)
        try {
            fixture.store.init(fixture.owner)
            runCurrent()
            assertTrue(fixture.store.state.value.backupAvailable)
            assertEquals(
                listOf(Action.Backup.ObserveAuth, Action.Backup.ObservePreferences, Action.Backup.ObserveRestoreState),
                actions,
            )
            assertEquals(listOf("theme", "startMode", "archive"), fixture.interactor.observations)
            assertEquals(ThemeMode.DARK, fixture.store.state.value.themeMode)
            assertEquals(StartCardModeUi.LAGGING_GROUPS, fixture.store.state.value.startCardMode)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unavailableBackupInitializesNoObservers() = runTest {
        val handler = object : SettingsBackupHandler {
            override val isAvailable = false
            override fun invoke(action: Action.Backup): Unit = error("Unavailable backup invoked: $action")
        }
        val fixture = SettingsStoreFixture(StandardTestDispatcher(testScheduler), handler)
        try {
            fixture.store.init(fixture.owner)
            runCurrent()
            assertFalse(fixture.store.state.value.backupAvailable)
            assertEquals(listOf("theme", "startMode", "archive"), fixture.interactor.observations)
            assertEquals(ThemeMode.DARK, fixture.store.state.value.themeMode)
            assertEquals(StartCardModeUi.LAGGING_GROUPS, fixture.store.state.value.startCardMode)
            assertEquals(4, fixture.store.state.value.archivedCounts?.exercises)
            assertEquals(1, fixture.store.state.value.archivedCounts?.trainings)
        } finally {
            fixture.close()
        }
    }
}
