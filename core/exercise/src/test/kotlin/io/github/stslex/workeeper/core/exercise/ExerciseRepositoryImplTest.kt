// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise

import android.database.sqlite.SQLiteConstraintException
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.database.common.DbTransition
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository.SaveResult
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ExerciseRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val exerciseDao = mockk<ExerciseDao>(relaxed = true)
    private val tagDao = mockk<TagDao>(relaxed = true)
    private val exerciseTagDao = mockk<ExerciseTagDao>(relaxed = true)
    private val trainingExerciseDao = mockk<TrainingExerciseDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val setDao = mockk<SetDao>(relaxed = true)
    private val imageStorage = mockk<ImageStorage>(relaxed = true)
    private val transition = object : DbTransition {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }

    private val repository: ExerciseRepository = ExerciseRepositoryImpl(
        dao = exerciseDao,
        tagDao = tagDao,
        exerciseTagDao = exerciseTagDao,
        trainingExerciseDao = trainingExerciseDao,
        sessionDao = sessionDao,
        setDao = setDao,
        imageStorage = imageStorage,
        transition = transition,
        bgDispatcher = testDispatcher,
    )

    private fun exerciseEntity(
        uuid: Uuid,
        imagePath: String? = null,
    ): ExerciseEntity = ExerciseEntity(
        uuid = uuid,
        name = "name-$uuid",
        type = ExerciseTypeEntity.WEIGHTED,
        description = null,
        imagePath = imagePath,
        archived = false,
        createdAt = 0L,
        archivedAt = null,
        lastAdhocSets = null,
    )

    @Test
    fun `saveItem returns DuplicateName when DAO throws unique-constraint error`() = runTest(testDispatcher) {
        // saveItem branches on whether the exercise already exists. We force the insert path
        // by stubbing getById to return null, then making insert throw the unique-name
        // collision the production code maps to DuplicateName.
        coEvery { exerciseDao.getById(any()) } returns null
        coEvery {
            exerciseDao.insert(any())
        } throws SQLiteConstraintException("UNIQUE constraint failed: exercise_table.name")

        val result = repository.saveItem(
            ExerciseChangeDataModel(
                uuid = Uuid.random(),
                name = "Bench Press",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                timestamp = 0L,
                labels = emptyList(),
                lastAdHocSets = null,
            ),
        )

        assertEquals(SaveResult.DuplicateName, result)
        coVerify(exactly = 0) { exerciseTagDao.insert(any()) }
    }

    @Test
    fun `saveItem returns Success when DAO insert succeeds`() = runTest(testDispatcher) {
        coEvery { exerciseDao.getById(any()) } returns null
        coEvery { exerciseDao.insert(any()) } returns Unit

        val result = repository.saveItem(
            ExerciseChangeDataModel(
                uuid = Uuid.random(),
                name = "Squat",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                timestamp = 0L,
                labels = emptyList(),
                lastAdHocSets = null,
            ),
        )

        assertEquals(SaveResult.Success, result)
    }

    @Test
    fun `deleteItem removes the row and the image file when imagePath set`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val path = "/data/user/0/app/files/exercise_images/$uuid.jpg"
        coEvery { exerciseDao.getById(uuid) } returns exerciseEntity(uuid, imagePath = path)

        repository.deleteItem(uuid.toString())

        coVerify { exerciseDao.permanentDelete(uuid) }
        coVerify { imageStorage.deleteImage(path) }
    }

    @Test
    fun `deleteItem skips imageStorage when imagePath is null`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        coEvery { exerciseDao.getById(uuid) } returns exerciseEntity(uuid, imagePath = null)

        repository.deleteItem(uuid.toString())

        coVerify { exerciseDao.permanentDelete(uuid) }
        coVerify(exactly = 0) { imageStorage.deleteImage(any()) }
    }

    @Test
    fun `deleteAllItems removes rows and image files`() = runTest(testDispatcher) {
        val uuidA = Uuid.random()
        val uuidB = Uuid.random()
        val pathA = "/files/$uuidA.jpg"
        coEvery { exerciseDao.getById(uuidA) } returns exerciseEntity(uuidA, imagePath = pathA)
        coEvery { exerciseDao.getById(uuidB) } returns exerciseEntity(uuidB, imagePath = null)

        repository.deleteAllItems(listOf(uuidA, uuidB))

        coVerify { exerciseDao.permanentDelete(uuidA) }
        coVerify { exerciseDao.permanentDelete(uuidB) }
        coVerify(exactly = 1) { imageStorage.deleteImage(pathA) }
    }

    @Test
    fun `archive does not delete the image file`() = runTest(testDispatcher) {
        val uuid = Uuid.random()

        repository.archive(uuid.toString())

        coVerify { exerciseDao.archive(uuid, any()) }
        coVerify(exactly = 0) { imageStorage.deleteImage(any()) }
    }

    @Test
    fun `permanentDelete removes the image file`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val path = "/files/$uuid.jpg"
        coEvery { exerciseDao.getById(uuid) } returns exerciseEntity(uuid, imagePath = path)

        repository.permanentDelete(uuid.toString())

        coVerify { exerciseDao.permanentDelete(uuid) }
        coVerify { imageStorage.deleteImage(path) }
    }

    @Test
    fun `bulkPermanentDelete removes image files for all rows`() = runTest(testDispatcher) {
        val uuidA = Uuid.random()
        val uuidB = Uuid.random()
        val pathA = "/files/$uuidA.jpg"
        coEvery { exerciseDao.getById(uuidA) } returns exerciseEntity(uuidA, imagePath = pathA)
        coEvery { exerciseDao.getById(uuidB) } returns exerciseEntity(uuidB, imagePath = null)

        repository.bulkPermanentDelete(setOf(uuidA.toString(), uuidB.toString()))

        coVerify { exerciseDao.permanentDelete(uuidA) }
        coVerify { exerciseDao.permanentDelete(uuidB) }
        coVerify(exactly = 1) { imageStorage.deleteImage(pathA) }
    }
}
