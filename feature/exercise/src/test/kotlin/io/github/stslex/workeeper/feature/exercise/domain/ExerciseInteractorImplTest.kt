// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.SaveResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ExerciseInteractorImplTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val interactor = ExerciseInteractorImpl(
        exerciseRepository = exerciseRepository,
        tagRepository = tagRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `saveExercise forwards the snapshot uuid to repository on Success`() = runTest {
        val captured = slot<ExerciseChangeDataModel>()
        coEvery { exerciseRepository.saveItem(capture(captured)) } returns ExerciseRepository.SaveResult.Success

        val uuid = Uuid.random()
        val result = interactor.saveExercise(
            ExerciseChangeDataModel(
                uuid = uuid,
                name = "Bench",
                timestamp = 0L,
                lastAdHocSets = null,
            ),
        )

        assertTrue(result is SaveResult.Success)
        assertEquals(uuid, (result as SaveResult.Success).resolvedUuid)
        assertEquals(uuid, captured.captured.uuid)
        coVerify { exerciseRepository.saveItem(any()) }
    }

    @Test
    fun `saveExercise propagates the lastAdHocSets payload`() = runTest {
        val captured = slot<ExerciseChangeDataModel>()
        coEvery { exerciseRepository.saveItem(capture(captured)) } returns ExerciseRepository.SaveResult.Success

        interactor.saveExercise(
            ExerciseChangeDataModel(
                uuid = Uuid.random(),
                name = "Bench",
                timestamp = 0L,
                lastAdHocSets = null,
            ),
        )

        assertEquals(null, captured.captured.lastAdHocSets)
    }

    @Test
    fun `saveExercise propagates DuplicateName from repository`() = runTest {
        coEvery { exerciseRepository.saveItem(any()) } returns ExerciseRepository.SaveResult.DuplicateName

        val result = interactor.saveExercise(
            ExerciseChangeDataModel(
                uuid = Uuid.random(),
                name = "Bench",
                timestamp = 0L,
                lastAdHocSets = null,
            ),
        )

        assertEquals(SaveResult.DuplicateName, result)
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

    @Test
    fun `canPermanentlyDelete delegates to repository`() = runTest {
        coEvery { exerciseRepository.canPermanentlyDeleteImmediately("uuid-1") } returns true
        assertTrue(interactor.canPermanentlyDelete("uuid-1"))
    }

    @Test
    fun `permanentlyDelete delegates to repository`() = runTest {
        interactor.permanentlyDelete("uuid-1")
        coVerify { exerciseRepository.permanentDelete("uuid-1") }
    }
}
