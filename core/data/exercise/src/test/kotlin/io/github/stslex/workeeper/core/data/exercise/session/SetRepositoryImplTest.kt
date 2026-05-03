// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SetRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<SetDao>(relaxed = true)
    private val repository = SetRepositoryImpl(
        dao = dao,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `upsert keeps existing uuid when row already exists`() = runTest(testDispatcher) {
        val performedExerciseUuid = Uuid.random()
        val existing = SetEntity(
            uuid = Uuid.random(),
            performedExerciseUuid = performedExerciseUuid,
            position = 2,
            reps = 5,
            weight = 90.0,
            type = SetTypeEntity.WORK,
        )
        coEvery { dao.getByPerformedAndPosition(performedExerciseUuid, 2) } returns existing

        repository.upsert(
            performedExerciseUuid = performedExerciseUuid.toString(),
            position = 2,
            weight = 100.0,
            reps = 6,
            type = SetsDataType.WORK,
        )

        val captured = slot<SetEntity>()
        coVerify(exactly = 1) { dao.insert(capture(captured)) }
        assertEquals(existing.uuid, captured.captured.uuid)
        assertEquals(performedExerciseUuid, captured.captured.performedExerciseUuid)
        assertEquals(2, captured.captured.position)
        assertEquals(6, captured.captured.reps)
        assertEquals(100.0, captured.captured.weight)
        assertEquals(SetTypeEntity.WORK, captured.captured.type)
    }

    @Test
    fun `upsert creates new row when set is missing`() = runTest(testDispatcher) {
        val performedExerciseUuid = Uuid.random()
        coEvery { dao.getByPerformedAndPosition(performedExerciseUuid, 0) } returns null

        repository.upsert(
            performedExerciseUuid = performedExerciseUuid.toString(),
            position = 0,
            weight = null,
            reps = 12,
            type = SetsDataType.WARM,
        )

        val captured = slot<SetEntity>()
        coVerify(exactly = 1) { dao.insert(capture(captured)) }
        assertEquals(performedExerciseUuid, captured.captured.performedExerciseUuid)
        assertEquals(0, captured.captured.position)
        assertEquals(12, captured.captured.reps)
        assertEquals(null, captured.captured.weight)
        assertEquals(SetTypeEntity.WARM, captured.captured.type)
    }

    @Test
    fun `count and existence helpers delegate to DAO`() = runTest(testDispatcher) {
        val performedExerciseUuid = Uuid.random()
        coEvery { dao.hasAnyForPerformed(performedExerciseUuid) } returns true
        coEvery { dao.countByPerformedExercise(performedExerciseUuid) } returns 4

        val hasAny = repository.hasAnyForPerformed(performedExerciseUuid.toString())
        val count = repository.countByPerformedExercise(performedExerciseUuid.toString())

        assertEquals(true, hasAny)
        assertEquals(4, count)
        coVerify(exactly = 1) { dao.hasAnyForPerformed(performedExerciseUuid) }
        coVerify(exactly = 1) { dao.countByPerformedExercise(performedExerciseUuid) }
    }

    @Test
    fun `delete helpers delegate to DAO`() = runTest(testDispatcher) {
        val performedExerciseUuid = Uuid.random()

        repository.deleteByPerformedAndPosition(
            performedExerciseUuid = performedExerciseUuid.toString(),
            position = 3,
        )
        repository.deleteAllForPerformedExercise(performedExerciseUuid.toString())

        coVerify(exactly = 1) { dao.deleteByPerformedAndPosition(performedExerciseUuid, 3) }
        coVerify(exactly = 1) { dao.deleteAllForPerformedExercise(performedExerciseUuid) }
    }
}
