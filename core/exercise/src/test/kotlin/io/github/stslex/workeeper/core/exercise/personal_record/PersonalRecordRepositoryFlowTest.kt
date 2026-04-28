// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.personal_record

import io.github.stslex.workeeper.core.database.session.PersonalRecordRow
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class PersonalRecordRepositoryFlowTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<SessionDao>()
    private val repository: PersonalRecordRepository = PersonalRecordRepositoryImpl(
        sessionDao = dao,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `observePersonalRecord maps row to data model`() = runTest(testDispatcher) {
        val exerciseUuid = Uuid.random()
        val row = sampleRow(weight = 100.0, reps = 5)
        every {
            dao.observePersonalRecord(exerciseUuid, isWeightless = false)
        } returns MutableStateFlow(row)

        val result = repository
            .observePersonalRecord(exerciseUuid.toString(), ExerciseTypeDataModel.WEIGHTED)
            .first()

        assertEquals(100.0, result?.weight)
        assertEquals(5, result?.reps)
        assertEquals(row.setUuid.toString(), result?.setUuid)
    }

    @Test
    fun `observePersonalRecord maps null to null`() = runTest(testDispatcher) {
        val exerciseUuid = Uuid.random()
        every {
            dao.observePersonalRecord(exerciseUuid, isWeightless = true)
        } returns MutableStateFlow<PersonalRecordRow?>(null)

        val result = repository
            .observePersonalRecord(exerciseUuid.toString(), ExerciseTypeDataModel.WEIGHTLESS)
            .first()

        assertNull(result)
    }

    @Test
    fun `observePersonalRecords with empty input emits empty map`() = runTest(testDispatcher) {
        val result = repository.observePersonalRecords(emptyMap()).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observePersonalRecords returns map keyed by uuid`() = runTest(testDispatcher) {
        val exerciseA = Uuid.random()
        val exerciseB = Uuid.random()
        every {
            dao.observePersonalRecord(exerciseA, isWeightless = false)
        } returns MutableStateFlow(sampleRow(weight = 100.0, reps = 5))
        every {
            dao.observePersonalRecord(exerciseB, isWeightless = true)
        } returns MutableStateFlow(sampleRow(weight = null, reps = 20))

        val result = repository.observePersonalRecords(
            mapOf(
                exerciseA.toString() to ExerciseTypeDataModel.WEIGHTED,
                exerciseB.toString() to ExerciseTypeDataModel.WEIGHTLESS,
            ),
        ).first()

        assertEquals(2, result.size)
        assertEquals(100.0, result[exerciseA.toString()]?.weight)
        assertEquals(20, result[exerciseB.toString()]?.reps)
    }

    @Test
    fun `observePersonalRecords re-emits when one underlying flow updates`() = runTest(testDispatcher) {
        val exerciseA = Uuid.random()
        val exerciseB = Uuid.random()
        val flowA = MutableStateFlow(sampleRow(weight = 100.0, reps = 5))
        val flowB = MutableStateFlow(sampleRow(weight = null, reps = 12))
        every { dao.observePersonalRecord(exerciseA, isWeightless = false) } returns flowA
        every { dao.observePersonalRecord(exerciseB, isWeightless = true) } returns flowB

        val flow = repository.observePersonalRecords(
            mapOf(
                exerciseA.toString() to ExerciseTypeDataModel.WEIGHTED,
                exerciseB.toString() to ExerciseTypeDataModel.WEIGHTLESS,
            ),
        )

        val initial = flow.first()
        assertEquals(100.0, initial[exerciseA.toString()]?.weight)
        assertEquals(12, initial[exerciseB.toString()]?.reps)

        flowA.value = sampleRow(weight = 110.0, reps = 5)

        val updated = flow.first()
        assertEquals(110.0, updated[exerciseA.toString()]?.weight)
        assertEquals(12, updated[exerciseB.toString()]?.reps)
    }

    private fun sampleRow(
        weight: Double?,
        reps: Int,
    ): PersonalRecordRow = PersonalRecordRow(
        sessionUuid = Uuid.random(),
        performedExerciseUuid = Uuid.random(),
        setUuid = Uuid.random(),
        weight = weight,
        reps = reps,
        type = SetTypeEntity.WORK,
        finishedAt = 1_000L,
    )
}
