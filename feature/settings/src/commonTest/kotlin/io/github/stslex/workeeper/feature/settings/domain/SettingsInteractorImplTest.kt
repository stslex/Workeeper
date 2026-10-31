// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsCommonDataStoreFake
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsExerciseRepositoryFake
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsTrainingRepositoryFake
import io.github.stslex.workeeper.feature.settings.fixtures.settingsTestPlatformInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class SettingsInteractorImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val commonDataStore = SettingsCommonDataStoreFake()
    private val platformInfo = settingsTestPlatformInfo()
    private val exerciseRepository = SettingsExerciseRepositoryFake()
    private val trainingRepository = SettingsTrainingRepositoryFake()

    private lateinit var interactor: SettingsInteractor

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        interactor = SettingsInteractorImpl(
            platformInfo = platformInfo,
            commonDataStore = commonDataStore,
            exerciseRepository = exerciseRepository,
            trainingRepository = trainingRepository,
            defaultDispatcher = testDispatcher,
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `observeThemeMode maps stored string to ThemeMode`() = runTest(testDispatcher) {
        commonDataStore.themePreference = flowOf("DARK")
        assertEquals(ThemeModeDomain.DARK, interactor.observeThemeMode().first())
    }

    @Test
    fun `observeThemeMode returns SYSTEM for unknown stored value`() = runTest(testDispatcher) {
        commonDataStore.themePreference = flowOf("INVALID")
        assertEquals(ThemeModeDomain.SYSTEM, interactor.observeThemeMode().first())
    }

    @Test
    fun `setThemeMode forwards enum name to data store`() = runTest(testDispatcher) {
        interactor.setThemeMode(ThemeModeDomain.LIGHT)
        assertEquals(listOf(ThemeModeDomain.LIGHT.value), commonDataStore.themes)
    }

    @Test
    fun `observeStartCardMode maps stored string to the domain mode`() = runTest(testDispatcher) {
        commonDataStore.homeStartCardMode = flowOf("FORGOTTEN_TRAINING")
        assertEquals(
            StartCardModeDomain.FORGOTTEN_TRAINING,
            interactor.observeStartCardMode().first(),
        )
    }

    @Test
    fun `observeStartCardMode returns WEEK for unknown stored value`() = runTest(testDispatcher) {
        commonDataStore.homeStartCardMode = flowOf("INVALID")
        assertEquals(StartCardModeDomain.WEEK, interactor.observeStartCardMode().first())
    }

    @Test
    fun `setStartCardMode forwards the persistence encoding to the data store`() =
        runTest(testDispatcher) {
            interactor.setStartCardMode(StartCardModeDomain.LAGGING_GROUPS)
            assertEquals(listOf("LAGGING_GROUPS"), commonDataStore.modes)
        }

    /**
     * GUARD: these strings must match feature/home's enum of the same name — two features share
     * the one preference — and WEEK is what an absent key reads as.
     */
    @Test
    fun `the storage encoding and the WEEK default are pinned`() {
        assertEquals("WEEK", StartCardModeDomain.WEEK.value)
        assertEquals("DAYS_SINCE_LAST", StartCardModeDomain.DAYS_SINCE_LAST.value)
        assertEquals("LAGGING_GROUPS", StartCardModeDomain.LAGGING_GROUPS.value)
        assertEquals("FORGOTTEN_TRAINING", StartCardModeDomain.FORGOTTEN_TRAINING.value)
        assertEquals(StartCardModeDomain.WEEK, StartCardModeDomain.fromValue("not-a-mode"))
    }

    @Test
    fun `observeArchivedCounts combines the two counts that already existed`() =
        runTest(testDispatcher) {
            exerciseRepository.archivedCount = flowOf(4)
            trainingRepository.archivedCount = flowOf(1)

            val counts = interactor.observeArchivedCounts().first()

            assertEquals(4, counts.exercises)
            assertEquals(1, counts.trainings)
        }
}
