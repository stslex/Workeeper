// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SetDaoTest : BaseDatabaseTest() {

    private val setDao get() = database.setDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val sessionDao get() = database.sessionDao
    private val trainingDao get() = database.trainingDao
    private val exerciseDao get() = database.exerciseDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `getByPerformedExercises with empty input returns empty list`() = runTest {
        val rows = setDao.getByPerformedExercises(emptyList())

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `getByPerformedExercises with a single uuid returns the same rows as getByPerformedExercise`() =
        runTest {
            val performedUuid = seedPerformed()
            seedSet(performedUuid, position = 1, weight = 110.0)
            seedSet(performedUuid, position = 0, weight = 100.0)

            val single = setDao.getByPerformedExercise(performedUuid)
            val batch = setDao.getByPerformedExercises(listOf(performedUuid))

            // Same uuids, same order semantics within a single performed exercise.
            assertEquals(single.map { it.uuid }, batch.map { it.uuid })
        }

    @Test
    fun `getByPerformedExercises returns rows for every performed uuid that has sets`() = runTest {
        val firstPerformed = seedPerformed(name = "Bench")
        val secondPerformed = seedPerformed(name = "Squat")
        val thirdPerformedNoSets = seedPerformed(name = "Lunge")
        seedSet(firstPerformed, position = 0, weight = 100.0)
        seedSet(firstPerformed, position = 1, weight = 110.0)
        seedSet(secondPerformed, position = 0, weight = 200.0)

        val rows = setDao.getByPerformedExercises(
            listOf(firstPerformed, secondPerformed, thirdPerformedNoSets),
        )

        // Kotlin-side groupBy in callers will skip uuids with no rows; the DAO simply
        // returns no rows for them. Performed exercise without sets must not surface.
        val grouped = rows.groupBy { it.performedExerciseUuid }
        assertEquals(setOf(firstPerformed, secondPerformed), grouped.keys)
        assertEquals(2, grouped.getValue(firstPerformed).size)
        assertEquals(1, grouped.getValue(secondPerformed).size)
    }

    @Test
    fun `getByPerformedExercises orders sets within each performed uuid by position ascending`() =
        runTest {
            val performedUuid = seedPerformed()
            // Insert positions out of order to confirm the ORDER BY is observable.
            seedSet(performedUuid, position = 2, weight = 130.0)
            seedSet(performedUuid, position = 0, weight = 100.0)
            seedSet(performedUuid, position = 1, weight = 110.0)

            val rows = setDao.getByPerformedExercises(listOf(performedUuid))
            val byPerformed = rows.groupBy { it.performedExerciseUuid }

            assertEquals(listOf(0, 1, 2), byPerformed.getValue(performedUuid).map { it.position })
        }

    @Test
    fun `getByPerformedExercises silently ignores uuids that are not in the database`() = runTest {
        val performedUuid = seedPerformed()
        seedSet(performedUuid, position = 0, weight = 100.0)
        val unknownPerformedUuid = Uuid.random()

        val rows = setDao.getByPerformedExercises(listOf(performedUuid, unknownPerformedUuid))

        // No FK violation is raised; the unknown uuid is simply absent from the result.
        assertEquals(setOf(performedUuid), rows.map { it.performedExerciseUuid }.toSet())
    }

    private suspend fun seedPerformed(name: String = "Bench-${Uuid.random()}"): Uuid {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        val sessionUuid = Uuid.random()
        val performedUuid = Uuid.random()
        trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push-$trainingUuid",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = name,
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            ),
        )
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = performedUuid,
                    sessionUuid = sessionUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = false,
                ),
            ),
        )
        return performedUuid
    }

    private suspend fun seedSet(
        performedExerciseUuid: Uuid,
        position: Int,
        weight: Double,
    ) {
        setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = performedExerciseUuid,
                position = position,
                reps = 5,
                weight = weight,
                type = SetTypeEntity.WORK,
            ),
        )
    }
}
