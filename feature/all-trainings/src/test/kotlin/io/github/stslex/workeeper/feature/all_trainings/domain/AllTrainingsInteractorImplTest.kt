// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.BulkArchiveOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AllTrainingsInteractorImplTest {

    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val interactor = AllTrainingsInteractorImpl(
        trainingRepository = trainingRepository,
        tagRepository = tagRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `archiveTrainings delegates to repository bulkArchive`() = runTest {
        coEvery { trainingRepository.bulkArchive(any()) } returns BulkArchiveOutcome(2, emptyList())

        val outcome = interactor.archiveTrainings(setOf("a", "b"))

        assertEquals(2, outcome.archivedCount)
        coVerify { trainingRepository.bulkArchive(setOf("a", "b")) }
    }

    @Test
    fun `deleteTrainings returns target count and delegates`() = runTest {
        val outcome = interactor.deleteTrainings(setOf("a", "b"))

        assertEquals(2, outcome)
        coVerify { trainingRepository.bulkPermanentDelete(setOf("a", "b")) }
    }

    @Test
    fun `canPermanentlyDelete delegates to repository`() = runTest {
        coEvery { trainingRepository.canBulkPermanentDelete(any()) } returns true
        assertTrue(interactor.canPermanentlyDelete(setOf("a")))
    }
}
