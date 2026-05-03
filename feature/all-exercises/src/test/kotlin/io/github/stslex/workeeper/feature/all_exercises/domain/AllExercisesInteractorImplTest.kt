// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AllExercisesInteractorImplTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val interactor = AllExercisesInteractorImpl(
        exerciseRepository = exerciseRepository,
        tagRepository = tagRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `archiveExercise returns Success when no active trainings`() = runTest {
        coEvery { exerciseRepository.getActiveTrainingsUsing("uuid-1") } returns emptyList()

        val result = interactor.archiveExercise("uuid-1")

        assertTrue(result is ArchiveResult.Success)
        coVerify { exerciseRepository.archive("uuid-1") }
    }

    @Test
    fun `archiveExercise returns Blocked when active trainings exist`() = runTest {
        coEvery { exerciseRepository.getActiveTrainingsUsing("uuid-1") } returns listOf("Push", "Pull")

        val result = interactor.archiveExercise("uuid-1")

        assertTrue(result is ArchiveResult.Blocked)
        assertEquals(listOf("Push", "Pull"), (result as ArchiveResult.Blocked).activeTrainings)
        coVerify(exactly = 0) { exerciseRepository.archive(any()) }
    }

    @Test
    fun `restoreExercise delegates to repository`() = runTest {
        interactor.restoreExercise("uuid-1")
        coVerify { exerciseRepository.restore("uuid-1") }
    }

    @Test
    fun `canPermanentlyDelete delegates to repository`() = runTest {
        coEvery { exerciseRepository.canPermanentlyDeleteImmediately("uuid-1") } returns true
        assertTrue(interactor.canPermanentlyDelete("uuid-1"))
        coVerify { exerciseRepository.canPermanentlyDeleteImmediately("uuid-1") }
    }

    @Test
    fun `permanentlyDelete delegates to repository`() = runTest {
        interactor.permanentlyDelete("uuid-1")
        coVerify { exerciseRepository.permanentDelete("uuid-1") }
    }
}
