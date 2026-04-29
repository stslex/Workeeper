// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.training

import io.github.stslex.workeeper.core.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TrainingRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val trainingDao = mockk<TrainingDao>(relaxed = true)
    private val trainingExerciseDao = mockk<TrainingExerciseDao>(relaxed = true)
    private val tagDao = mockk<TagDao>(relaxed = true)
    private val trainingTagDao = mockk<TrainingTagDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)

    private val repository = TrainingRepositoryImpl(
        dao = trainingDao,
        trainingExerciseDao = trainingExerciseDao,
        tagDao = tagDao,
        trainingTagDao = trainingTagDao,
        sessionDao = sessionDao,
        exerciseRepository = exerciseRepository,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `syncExercises with new exercise that has adhocPlan copies adhocPlan to plan_sets`() = runTest {
        val trainingUuid = "00000000-0000-0000-0000-000000000001"
        val exerciseUuid = "00000000-0000-0000-0000-0000000000a1"
        val plan = listOf(
            PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
        )
        coEvery { trainingExerciseDao.getByTraining(any()) } returns emptyList()
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns plan

        val rowsSlot = slot<List<TrainingExerciseEntity>>()
        coEvery { trainingExerciseDao.insert(capture(rowsSlot)) } returns Unit

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                timestamp = 0L,
                exerciseUuids = listOf(exerciseUuid),
            ),
        )

        val written = rowsSlot.captured.single()
        val decoded = PlanSetsConverter.fromJson(written.planSets)
        assertEquals(plan, decoded)
    }

    @Test
    fun `syncExercises with new exercise that has no adhocPlan stores null planSets`() = runTest {
        val trainingUuid = "00000000-0000-0000-0000-000000000001"
        val exerciseUuid = "00000000-0000-0000-0000-0000000000a1"
        coEvery { trainingExerciseDao.getByTraining(any()) } returns emptyList()
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns null

        val rowsSlot = slot<List<TrainingExerciseEntity>>()
        coEvery { trainingExerciseDao.insert(capture(rowsSlot)) } returns Unit

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                timestamp = 0L,
                exerciseUuids = listOf(exerciseUuid),
            ),
        )

        assertNull(rowsSlot.captured.single().planSets)
    }

    @Test
    fun `syncExercises with existing exercise preserves non-empty planSets even if adhocPlan changed`() = runTest {
        val trainingUuid = "00000000-0000-0000-0000-000000000001"
        val exerciseUuid = "00000000-0000-0000-0000-0000000000a1"
        val existingJson = """[{"weight":100.0,"reps":3,"type":"WORK"}]"""
        coEvery { trainingExerciseDao.getByTraining(any()) } returns listOf(
            TrainingExerciseEntity(
                trainingUuid = Uuid.parse(trainingUuid),
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = 0,
                planSets = existingJson,
            ),
        )
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns listOf(
            PlanSetDataModel(weight = 999.0, reps = 1, type = SetTypeDataModel.WORK),
        )

        val rowsSlot = slot<List<TrainingExerciseEntity>>()
        coEvery { trainingExerciseDao.insert(capture(rowsSlot)) } returns Unit

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                timestamp = 0L,
                exerciseUuids = listOf(exerciseUuid),
            ),
        )

        assertEquals(existingJson, rowsSlot.captured.single().planSets)
        coVerify(exactly = 0) { exerciseRepository.getAdhocPlan(exerciseUuid) }
    }

    @Test
    fun `syncExercises with existing exercise preserves empty planSets (no fallback to adhocPlan)`() = runTest {
        val trainingUuid = "00000000-0000-0000-0000-000000000001"
        val exerciseUuid = "00000000-0000-0000-0000-0000000000a1"
        coEvery { trainingExerciseDao.getByTraining(any()) } returns listOf(
            TrainingExerciseEntity(
                trainingUuid = Uuid.parse(trainingUuid),
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = 0,
                planSets = "[]",
            ),
        )
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns listOf(
            PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
        )

        val rowsSlot = slot<List<TrainingExerciseEntity>>()
        coEvery { trainingExerciseDao.insert(capture(rowsSlot)) } returns Unit

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                timestamp = 0L,
                exerciseUuids = listOf(exerciseUuid),
            ),
        )

        assertEquals("[]", rowsSlot.captured.single().planSets)
        coVerify(exactly = 0) { exerciseRepository.getAdhocPlan(exerciseUuid) }
    }

    @Test
    fun `syncExercises with existing exercise preserves null planSets (no re-apply on subsequent saves)`() = runTest {
        val trainingUuid = "00000000-0000-0000-0000-000000000001"
        val exerciseUuid = "00000000-0000-0000-0000-0000000000a1"
        coEvery { trainingExerciseDao.getByTraining(any()) } returns listOf(
            TrainingExerciseEntity(
                trainingUuid = Uuid.parse(trainingUuid),
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = 0,
                planSets = null,
            ),
        )
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid) } returns listOf(
            PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
        )

        val rowsSlot = slot<List<TrainingExerciseEntity>>()
        coEvery { trainingExerciseDao.insert(capture(rowsSlot)) } returns Unit

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                timestamp = 0L,
                exerciseUuids = listOf(exerciseUuid),
            ),
        )

        assertNull(rowsSlot.captured.single().planSets)
        coVerify(exactly = 0) { exerciseRepository.getAdhocPlan(exerciseUuid) }
    }
}
