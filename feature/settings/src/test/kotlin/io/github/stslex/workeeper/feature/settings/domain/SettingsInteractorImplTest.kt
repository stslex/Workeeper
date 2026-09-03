// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain
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
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)

    private lateinit var interactor: SettingsInteractor

    @BeforeEach
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

    @Test
    fun `observeStartCardMode maps stored string to the domain mode`() = runTest(testDispatcher) {
        every { commonDataStore.homeStartCardMode } returns flowOf("FORGOTTEN_TRAINING")
        assertEquals(
            StartCardModeDomain.FORGOTTEN_TRAINING,
            interactor.observeStartCardMode().first(),
        )
    }

    @Test
    fun `observeStartCardMode returns WEEK for unknown stored value`() = runTest(testDispatcher) {
        every { commonDataStore.homeStartCardMode } returns flowOf("INVALID")
        assertEquals(StartCardModeDomain.WEEK, interactor.observeStartCardMode().first())
    }

    @Test
    fun `setStartCardMode forwards the persistence encoding to the data store`() =
        runTest(testDispatcher) {
            coEvery { commonDataStore.setHomeStartCardMode(any()) } returns Unit
            interactor.setStartCardMode(StartCardModeDomain.LAGGING_GROUPS)
            coVerify(exactly = 1) { commonDataStore.setHomeStartCardMode("LAGGING_GROUPS") }
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
            every { exerciseRepository.observeArchivedCount() } returns flowOf(4)
            every { trainingRepository.observeArchivedCount() } returns flowOf(1)

            val counts = interactor.observeArchivedCounts().first()

            assertEquals(4, counts.exercises)
            assertEquals(1, counts.trainings)
        }
}
