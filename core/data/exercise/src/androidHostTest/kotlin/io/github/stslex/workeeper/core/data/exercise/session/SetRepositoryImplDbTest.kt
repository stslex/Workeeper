// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.mockk.coVerify
import io.mockk.spyk
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
            position = 0,
            type = SetsDataType.WORK,
        )

        repository.insert(performedUuid.toString(), set = data)

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
            position = 0,
            type = SetsDataType.WORK,
        )
        repository.insert(performedUuid.toString(), set = original)

        val updated = original.copy(reps = 8, weight = 95.0, type = SetsDataType.FAIL)
        repository.update(performedUuid.toString(), set = updated)

        val rows = repository.getByPerformedExercise(performedUuid.toString())
        assertEquals(updated, rows.single())
    }

    @Test
    fun `phone edit of set zero stays valid after the plan target advances to set one`() =
        runTest {
            val epoch = prepareWearSyncStorage(env.rawDatabase(), rotateDatabaseEpoch = false)
            val training = env.seedTraining()
            val exercise = env.seedExercise()
            val session = env.seedSession(trainingUuid = training.uuid)
            val performed = env.seedPerformed(session.uuid, exercise.uuid)
            env.seedTrainingExercise(
                trainingUuid = training.uuid,
                exerciseUuid = exercise.uuid,
                planSets = PlanSetsConverter.toJson(
                    listOf(
                        PlanSetDataModel(100.0, 5, SetTypeDataModel.WORK),
                        PlanSetDataModel(105.0, 6, SetTypeDataModel.WORK),
                    ),
                ),
            )
            val completed = SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 5,
                weight = 100.0,
                position = 0,
                type = SetsDataType.WORK,
            )
            repository.insert(performed.uuid.toString(), completed)
            val before = requireNotNull(env.rawDatabase().wearSyncDao.getSessionSync(session.uuid))
            assertEquals(
                1,
                env.rawDatabase().wearSyncDao.storeReceipt(
                    sessionUuid = session.uuid,
                    commandId = Uuid.random().toString(),
                    attemptFingerprint = ByteArray(34) { 1 },
                    databaseEpoch = epoch,
                    revision = before.revision,
                ),
            )

            repository.update(
                performed.uuid.toString(),
                completed.copy(reps = 8, weight = 110.0),
            )

            val rows = repository.getByPerformedExercise(performed.uuid.toString())
            val after = requireNotNull(env.rawDatabase().wearSyncDao.getSessionSync(session.uuid))
            assertEquals(8, rows.single { it.position == 0 }.reps)
            assertEquals(1, rows.size)
            assertTrue(after.revision > before.revision)
            assertNull(after.receiptCommandId)
        }

    @Test
    fun `upsert keeps existing uuid when row already exists for that position`() = runTest {
        val performedUuid = seedPerformedExercise()
        val initial = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            position = 2,
            weight = 80.0,
            type = SetsDataType.WORK,
        )
        repository.insert(performedUuid.toString(), set = initial)

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
    fun `insert conflict keeps the transaction winner row identity`() = runTest {
        val performedUuid = seedPerformedExercise()
        val winner = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            position = 0,
            weight = 80.0,
            type = SetsDataType.WORK,
        )
        val contender = winner.copy(
            uuid = Uuid.random().toString(),
            reps = 6,
            weight = 90.0,
        )

        repository.insert(performedUuid.toString(), winner)
        repository.insert(performedUuid.toString(), contender)

        val row = repository.getByPerformedExercise(performedUuid.toString()).single()
        assertEquals(winner.uuid, row.uuid)
        assertEquals(6, row.reps)
        assertEquals(90.0, row.weight)
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
            position = 0,
            weight = 80.0,
            type = SetsDataType.WORK,
        )
        repository.insert(performedUuid.toString(), set = data)

        repository.delete(data.uuid)

        assertTrue(repository.getByPerformedExercise(performedUuid.toString()).isEmpty())
    }

    @Test
    fun `deleteByPerformedAndPosition only removes the row at that position`() = runTest {
        val performedUuid = seedPerformedExercise()
        repository.insert(
            performedUuid.toString(),
            set = SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 5,
                weight = 100.0,
                type = SetsDataType.WORK,
                position = 0,
            ),
        )
        repository.insert(
            performedUuid.toString(),
            set = SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 5,
                weight = 110.0,
                position = 1,
                type = SetsDataType.WORK,
            ),
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

            set = SetsDataModel(
                uuid = Uuid.random().toString(),
                reps = 5,
                weight = 100.0,
                position = 0,
                type = SetsDataType.WORK,
            ),
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
                set = SetsDataModel(
                    uuid = Uuid.random().toString(),
                    reps = 5,
                    weight = 100.0,
                    type = SetsDataType.WORK,
                    position = 0,
                ),
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
                set = SetsDataModel(firstUuid, 5, 100.0, SetsDataType.WORK, 0),
            )
            repository.insert(
                performedUuid.toString(),
                set = SetsDataModel(secondUuid, 5, 110.0, SetsDataType.WORK, 1),
            )
            repository.insert(
                performedUuid.toString(),
                set = SetsDataModel(thirdUuid, 5, 120.0, SetsDataType.WORK, 2),
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
        val data = SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK, 0)
        repository.insert(performedUuid.toString(), set = data)

        repository.reorderSets(performedUuid.toString(), orderedSetUuids = emptyList())

        val rows = env.setDao.getByPerformedExercise(performedUuid)
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().position)
    }

    @Test
    fun `reorderSets rejects a partial or foreign order without moving any row`() = runTest {
        val performedUuid = seedPerformedExercise()
        val firstUuid = Uuid.random().toString()
        val secondUuid = Uuid.random().toString()
        repository.insert(
            performedUuid.toString(),
            SetsDataModel(firstUuid, 5, 100.0, SetsDataType.WORK, 0),
        )
        repository.insert(
            performedUuid.toString(),
            SetsDataModel(secondUuid, 5, 110.0, SetsDataType.WORK, 1),
        )

        val partialFailure = try {
            repository.reorderSets(performedUuid.toString(), listOf(secondUuid))
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        val foreignFailure = try {
            repository.reorderSets(
                performedUuid.toString(),
                listOf(secondUuid, Uuid.random().toString()),
            )
            null
        } catch (error: IllegalArgumentException) {
            error
        }

        assertTrue(partialFailure is IllegalArgumentException)
        assertTrue(foreignFailure is IllegalArgumentException)
        val unchanged = env.setDao.getByPerformedExercise(performedUuid)
            .sortedBy { it.position }
            .map { it.uuid.toString() }
        assertEquals(listOf(firstUuid, secondUuid), unchanged)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `getLastFinishedSet returns null when no finished session has logged the exercise`() =
        runTest {
            val exerciseUuid = Uuid.random()
            assertNull(repository.getLastFinishedSet(exerciseUuid.toString()))
        }

    @Test
    fun `getByPerformedExercises with empty input returns empty Map and does not call the DAO`() =
        runTest {
            val spiedDao = spyk<SetDao>(env.setDao)
            val repositoryWithSpy = SetRepositoryImpl(
                dao = spiedDao,
                transition = env.transition,
                ioDispatcher = UnconfinedTestDispatcher(),
            )

            val result = repositoryWithSpy.getByPerformedExercises(emptyList())

            assertTrue(result.isEmpty())
            // Short-circuit: with no input, the DAO is never queried.
            coVerify(exactly = 0) { spiedDao.getByPerformedExercises(any()) }
        }

    @Test
    fun `getByPerformedExercises returns mapped sets keyed by stringified performed uuid`() =
        runTest {
            val firstPerformed = seedPerformedExercise()
            val secondPerformed = seedPerformedExercise()
            val firstA =
                SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK, position = 0)
            val firstB =
                SetsDataModel(Uuid.random().toString(), 5, 110.0, SetsDataType.WORK, position = 1)
            val secondA =
                SetsDataModel(Uuid.random().toString(), 8, 50.0, SetsDataType.WARM, position = 0)
            // Insert positions out of order to verify the DAO ORDER BY survives the mapping.
            repository.insert(firstPerformed.toString(), set = firstB)
            repository.insert(firstPerformed.toString(), set = firstA)
            repository.insert(secondPerformed.toString(), set = secondA)

            val result = repository.getByPerformedExercises(
                listOf(firstPerformed.toString(), secondPerformed.toString()),
            )

            assertEquals(setOf(firstPerformed.toString(), secondPerformed.toString()), result.keys)
            assertEquals(
                listOf(firstA.uuid, firstB.uuid),
                result.getValue(firstPerformed.toString()).map { it.uuid },
            )
            assertEquals(
                listOf(secondA),
                result.getValue(secondPerformed.toString()),
            )
        }

    @Test
    fun `getByPerformedExercises omits performed uuids that have no sets`() = runTest {
        val withSets = seedPerformedExercise()
        val withoutSets = seedPerformedExercise()
        repository.insert(
            withSets.toString(),
            set = SetsDataModel(Uuid.random().toString(), 5, 100.0, SetsDataType.WORK, 0),
        )

        val result = repository.getByPerformedExercises(
            listOf(withSets.toString(), withoutSets.toString()),
        )

        // Kotlin's `groupBy` only emits keys for entries that produced rows — uuids with
        // zero sets are absent from the map, not present with an empty list.
        assertEquals(setOf(withSets.toString()), result.keys)
        assertFalse(result.containsKey(withoutSets.toString()))
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
