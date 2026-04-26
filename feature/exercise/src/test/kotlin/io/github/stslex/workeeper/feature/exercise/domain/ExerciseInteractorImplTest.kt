// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ExerciseInteractorImplTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val interactor = ExerciseInteractorImpl(
        exerciseRepository = exerciseRepository,
        tagRepository = tagRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `saveExercise generates uuid when input is null`() = runTest {
        val captured = slot<ExerciseChangeDataModel>()
        coEvery { exerciseRepository.saveItem(capture(captured)) } returns Unit

        val resolved = interactor.saveExercise(
            ExerciseChangeDataModel(uuid = null, name = "Bench", timestamp = 0L),
        )

        assertNotNull(captured.captured.uuid)
        assertEquals(captured.captured.uuid, resolved)
        coVerify { exerciseRepository.saveItem(any()) }
    }

    @Test
    fun `saveExercise preserves uuid when provided`() = runTest {
        val captured = slot<ExerciseChangeDataModel>()
        coEvery { exerciseRepository.saveItem(capture(captured)) } returns Unit

        val resolved = interactor.saveExercise(
            ExerciseChangeDataModel(uuid = "fixed", name = "Bench", timestamp = 0L),
        )

        assertEquals("fixed", resolved)
        assertEquals("fixed", captured.captured.uuid)
    }

    @Test
    fun `archive returns Blocked when active trainings exist`() = runTest {
        coEvery { exerciseRepository.getActiveTrainingsUsing("uuid-1") } returns listOf("Push")

        val result = interactor.archive("uuid-1")
        assertTrue(result is ArchiveResult.Blocked)
        coVerify(exactly = 0) { exerciseRepository.archive(any()) }
    }

    @Test
    fun `archive returns Success when no active trainings`() = runTest {
        coEvery { exerciseRepository.getActiveTrainingsUsing("uuid-1") } returns emptyList()

        val result = interactor.archive("uuid-1")
        assertTrue(result is ArchiveResult.Success)
        coVerify { exerciseRepository.archive("uuid-1") }
    }
}
