// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise

import android.database.sqlite.SQLiteConstraintException
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
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

    private val repository: ExerciseRepository = ExerciseRepositoryImpl(
        dao = exerciseDao,
        tagDao = tagDao,
        exerciseTagDao = exerciseTagDao,
        trainingExerciseDao = trainingExerciseDao,
        sessionDao = sessionDao,
        setDao = setDao,
        bgDispatcher = testDispatcher,
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
}
