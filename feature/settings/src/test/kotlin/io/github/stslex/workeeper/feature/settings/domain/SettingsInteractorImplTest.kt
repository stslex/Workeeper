// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SettingsInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val commonDataStore = mockk<CommonDataStore>(relaxed = true)
    private val platformInfo = mockk<PlatformInfoProvider>(relaxed = true)

    private lateinit var interactor: SettingsInteractor

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        interactor = SettingsInteractorImpl(
            platformInfo = platformInfo,
            commonDataStore = commonDataStore,
            defaultDispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `observeThemeMode maps stored string to ThemeMode`() = runTest(testDispatcher) {
        every { commonDataStore.themePreference } returns flowOf("DARK")
        assertEquals(ThemeModeDomain.DARK, interactor.observeThemeMode().first())
    }

    @Test
    fun `observeThemeMode returns SYSTEM for unknown stored value`() = runTest(testDispatcher) {
        every { commonDataStore.themePreference } returns flowOf("INVALID")
        assertEquals(ThemeModeDomain.SYSTEM, interactor.observeThemeMode().first())
    }

    @Test
    fun `setThemeMode forwards enum name to data store`() = runTest(testDispatcher) {
        coEvery { commonDataStore.setThemePreference(any()) } returns Unit
        interactor.setThemeMode(ThemeModeDomain.LIGHT)
        coVerify(exactly = 1) { commonDataStore.setThemePreference(ThemeModeDomain.LIGHT.value) }
    }
}
