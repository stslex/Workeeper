// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import android.content.Context
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
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
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var interactor: SettingsInteractor

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        interactor = SettingsInteractorImpl(
            context = context,
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
        assertEquals(ThemeMode.DARK, interactor.observeThemeMode().first())
    }

    @Test
    fun `observeThemeMode returns SYSTEM for unknown stored value`() = runTest(testDispatcher) {
        every { commonDataStore.themePreference } returns flowOf("INVALID")
        assertEquals(ThemeMode.SYSTEM, interactor.observeThemeMode().first())
    }

    @Test
    fun `setThemeMode forwards enum name to data store`() = runTest(testDispatcher) {
        coEvery { commonDataStore.setThemePreference(any()) } returns Unit
        interactor.setThemeMode(ThemeMode.LIGHT)
        coVerify(exactly = 1) { commonDataStore.setThemePreference("LIGHT") }
    }

    @Test
    fun `restoreExercise delegates to repository`() = runTest(testDispatcher) {
        coEvery { exerciseRepository.restore(any()) } returns Unit
        interactor.restoreExercise("uuid-1")
        coVerify(exactly = 1) { exerciseRepository.restore("uuid-1") }
    }

    @Test
    fun `restoreTraining delegates to repository`() = runTest(testDispatcher) {
        coEvery { trainingRepository.restore(any()) } returns Unit
        interactor.restoreTraining("uuid-2")
        coVerify(exactly = 1) { trainingRepository.restore("uuid-2") }
    }

    @Test
    fun `permanentlyDeleteExercise delegates to repository`() = runTest(testDispatcher) {
        coEvery { exerciseRepository.permanentDelete(any()) } returns Unit
        interactor.permanentlyDeleteExercise("uuid-3")
        coVerify(exactly = 1) { exerciseRepository.permanentDelete("uuid-3") }
    }

    @Test
    fun `permanentlyDeleteTraining delegates to repository`() = runTest(testDispatcher) {
        coEvery { trainingRepository.permanentDelete(any()) } returns Unit
        interactor.permanentlyDeleteTraining("uuid-4")
        coVerify(exactly = 1) { trainingRepository.permanentDelete("uuid-4") }
    }

    @Test
    fun `countExerciseSessions delegates to repository`() = runTest(testDispatcher) {
        coEvery { exerciseRepository.countSessionsUsing("uuid-5") } returns 7
        assertEquals(7, interactor.countExerciseSessions("uuid-5"))
    }

    @Test
    fun `countTrainingSessions delegates to repository`() = runTest(testDispatcher) {
        coEvery { trainingRepository.countSessionsUsing("uuid-6") } returns 3
        assertEquals(3, interactor.countTrainingSessions("uuid-6"))
    }
}
