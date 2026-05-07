// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SetRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: SetRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = SetRepositoryImpl(
            dao = env.setDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `insert and getByPerformedExercise read back the persisted set`() = runTest {
        val performedUuid = seedPerformedExercise()
        val data = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            weight = 100.0,
            type = SetsDataType.WORK,
        )

        repository.insert(performedUuid.toString(), position = 0, set = data)

        val rows = repository.getByPerformedExercise(performedUuid.toString())
        assertEquals(1, rows.size)
        assertEquals(data.copy(uuid = rows.single().uuid), rows.single())
    }

    @Test
    fun `update rewrites the row identified by uuid and position`() = runTest {
        val performedUuid = seedPerformedExercise()
        val original = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            weight = 80.0,
            type = SetsDataType.WORK,
        )
        repository.insert(performedUuid.toString(), position = 0, set = original)

        val updated = original.copy(reps = 8, weight = 95.0, type = SetsDataType.FAIL)
        repository.update(performedUuid.toString(), position = 0, set = updated)

        val rows = repository.getByPerformedExercise(performedUuid.toString())
        assertEquals(updated, rows.single())
    }

    @Test
    fun `upsert keeps existing uuid when row already exists for that position`() = runTest {
        val performedUuid = seedPerformedExercise()
        val initial = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            weight = 80.0,
            type = SetsDataType.WORK,
        )
        repository.insert(performedUuid.toString(), position = 2, set = initial)

        repository.upsert(
            performedExerciseUuid = performedUuid.toString(),
            position = 2,
            weight = 90.0,
            reps = 6,
            type = SetsDataType.WORK,
        )

        val rows = repository.getByPerformedExercise(performedUuid.toString())
        assertEquals(1, rows.size)
        assertEquals(initial.uuid, rows.single().uuid)
        assertEquals(90.0, rows.single().weight)
        assertEquals(6, rows.single().reps)
    }

    @Test
    fun `upsert mints a new uuid when no existing set occupies the position`() = runTest {
        val performedUuid = seedPerformedExercise()

        repository.upsert(
            performedExerciseUuid = performedUuid.toString(),
            position = 0,
            weight = null,
            reps = 12,
            type = SetsDataType.WARM,
        )

        val rows = repository.getByPerformedExercise(performedUuid.toString())
        assertEquals(1, rows.size)
        assertEquals(0, env.setDao.getByPerformedExercise(performedUuid).single().position)
        assertEquals(SetsDataType.WARM, rows.single().type)
        assertNull(rows.single().weight)
    }

    @Test
    fun `delete removes the row by uuid`() = runTest {
        val performedUuid = seedPerformedExercise()
        val data = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            weight = 80.0,
            type = SetsDataType.WORK,
        )
        repository.insert(performedUuid.toString(), position = 0, set = data)

        repository.delete(data.uuid)

        assertTrue(repository.getByPerformedExercise(performedUuid.toString()).isEmpty())
    }

    @Test
    fun `deleteByPerformedAndPosition only removes the row at that position`() = runTest {
        val performedUuid = seedPerformedExercise()
        repository.insert(
            performedUuid.toString(),
            position = 0,
            set = SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK),
        )
        repository.insert(
            performedUuid.toString(),
            position = 1,
            set = SetsDataModel(Uuid.random().toString(), 5, 110.0, SetsDataType.WORK),
        )

        repository.deleteByPerformedAndPosition(performedUuid.toString(), position = 0)

        val survivors = repository.getByPerformedExercise(performedUuid.toString())
        assertEquals(1, survivors.size)
        assertEquals(110.0, survivors.single().weight)
    }

    @Test
    fun `deleteAllForPerformedExercise wipes every row for that performed exercise`() = runTest {
        val performedUuid = seedPerformedExercise()
        repository.insert(
            performedUuid.toString(),
            position = 0,
            set = SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK),
        )

        repository.deleteAllForPerformedExercise(performedUuid.toString())

        assertTrue(repository.getByPerformedExercise(performedUuid.toString()).isEmpty())
    }

    @Test
    fun `hasAnyForPerformed and countByPerformedExercise reflect the persisted set count`() =
        runTest {
            val performedUuid = seedPerformedExercise()
            assertFalse(repository.hasAnyForPerformed(performedUuid.toString()))
            assertEquals(0, repository.countByPerformedExercise(performedUuid.toString()))

            repository.insert(
                performedUuid.toString(),
                position = 0,
                set = SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK),
            )

            assertTrue(repository.hasAnyForPerformed(performedUuid.toString()))
            assertEquals(1, repository.countByPerformedExercise(performedUuid.toString()))
        }

    @Test
    fun `reorderSets rewrites position values to match supplied order in one transaction`() =
        runTest {
            val performedUuid = seedPerformedExercise()
            val firstUuid = Uuid.random().toString()
            val secondUuid = Uuid.random().toString()
            val thirdUuid = Uuid.random().toString()
            repository.insert(
                performedUuid.toString(),
                position = 0,
                set = SetsDataModel(firstUuid, 5, 100.0, SetsDataType.WORK),
            )
            repository.insert(
                performedUuid.toString(),
                position = 1,
                set = SetsDataModel(secondUuid, 5, 110.0, SetsDataType.WORK),
            )
            repository.insert(
                performedUuid.toString(),
                position = 2,
                set = SetsDataModel(thirdUuid, 5, 120.0, SetsDataType.WORK),
            )

            // Reverse the order.
            repository.reorderSets(
                performedExerciseUuid = performedUuid.toString(),
                orderedSetUuids = listOf(thirdUuid, secondUuid, firstUuid),
            )

            val rows = env.setDao.getByPerformedExercise(performedUuid)
                .sortedBy { it.position }
                .map { it.uuid.toString() }
            assertEquals(listOf(thirdUuid, secondUuid, firstUuid), rows)
        }

    @Test
    fun `reorderSets with an empty list is a no-op`() = runTest {
        val performedUuid = seedPerformedExercise()
        val data = SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK)
        repository.insert(performedUuid.toString(), position = 0, set = data)

        repository.reorderSets(performedUuid.toString(), orderedSetUuids = emptyList())

        val rows = env.setDao.getByPerformedExercise(performedUuid)
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().position)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `getLastFinishedSet returns null when no finished session has logged the exercise`() =
        runTest {
            val exerciseUuid = Uuid.random()
            assertNull(repository.getLastFinishedSet(exerciseUuid.toString()))
        }

    private suspend fun seedPerformedExercise(): Uuid {
        val training = env.seedTraining()
        val exercise = env.seedExercise()
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = exercise.uuid,
        )
        return performed.uuid
    }
}
