// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
internal class PersonalRecordRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: PersonalRecordRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = PersonalRecordRepositoryImpl(
            sessionDao = env.sessionDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `getPersonalRecord returns the heaviest weighted set across finished sessions`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExercise()
        seedFinishedSet(trainingUuid, exerciseUuid, weight = 80.0, reps = 5, finishedAt = 1_000L)
        seedFinishedSet(trainingUuid, exerciseUuid, weight = 100.0, reps = 5, finishedAt = 2_000L)
        seedFinishedSet(trainingUuid, exerciseUuid, weight = 100.0, reps = 4, finishedAt = 3_000L)

        val pr = repository.getPersonalRecord(
            exerciseUuid = exerciseUuid.toString(),
            type = ExerciseTypeDataModel.WEIGHTED,
        )

        assertEquals(100.0, pr?.weight)
        // Reps DESC tiebreaker: 5 reps wins over 4 reps when weight is equal.
        assertEquals(5, pr?.reps)
    }

    @Test
    fun `getPersonalRecord returns the highest reps for weightless exercises`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExercise(
            type = ExerciseTypeEntity.WEIGHTLESS,
        )
        seedFinishedSet(trainingUuid, exerciseUuid, weight = null, reps = 8, finishedAt = 1_000L)
        seedFinishedSet(trainingUuid, exerciseUuid, weight = null, reps = 12, finishedAt = 2_000L)

        val pr = repository.getPersonalRecord(
            exerciseUuid = exerciseUuid.toString(),
            type = ExerciseTypeDataModel.WEIGHTLESS,
        )

        assertEquals(12, pr?.reps)
        assertNull(pr?.weight)
    }

    @Test
    fun `getPersonalRecord returns null when no finished sessions exist for the exercise`() =
        runTest {
            val (_, exerciseUuid) = seedTrainingAndExercise()

            val pr = repository.getPersonalRecord(
                exerciseUuid = exerciseUuid.toString(),
                type = ExerciseTypeDataModel.WEIGHTED,
            )

            assertNull(pr)
        }

    @Test
    fun `observePersonalRecord emits the live PR row`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExercise()
        seedFinishedSet(trainingUuid, exerciseUuid, weight = 100.0, reps = 5, finishedAt = 1_000L)

        val pr = repository.observePersonalRecord(
            exerciseUuid = exerciseUuid.toString(),
            type = ExerciseTypeDataModel.WEIGHTED,
        ).first()

        assertEquals(100.0, pr?.weight)
    }

    @Test
    fun `observePersonalRecords with empty input emits an empty map`() = runTest {
        val map = repository.observePersonalRecords(emptyMap()).first()

        assertTrue(map.isEmpty())
    }

    @Test
    fun `observePersonalRecords returns one PR per requested exercise`() = runTest {
        val (trainingUuid, weightedUuid) = seedTrainingAndExercise()
        val weightlessUuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = weightlessUuid,
                name = "Pull Up",
                type = ExerciseTypeEntity.WEIGHTLESS,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        seedFinishedSet(trainingUuid, weightedUuid, weight = 100.0, reps = 5, finishedAt = 1_000L)
        seedFinishedSet(
            trainingUuid,
            weightlessUuid,
            weight = null,
            reps = 12,
            finishedAt = 2_000L,
        )

        val records = repository.observePersonalRecords(
            uuidsByType = mapOf(
                weightedUuid.toString() to ExerciseTypeDataModel.WEIGHTED,
                weightlessUuid.toString() to ExerciseTypeDataModel.WEIGHTLESS,
            ),
        ).first()

        assertEquals(2, records.size)
        assertEquals(100.0, records[weightedUuid.toString()]?.weight)
        assertEquals(12, records[weightlessUuid.toString()]?.reps)
    }

    @Test
    fun `observePersonalRecordsBatch returns highest weighted PR per exercise`() = runTest {
        val (trainingUuid, weightedUuid) = seedTrainingAndExercise()
        val secondExerciseUuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = secondExerciseUuid,
                name = "Pull",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        seedFinishedSet(trainingUuid, weightedUuid, weight = 80.0, reps = 5, finishedAt = 1_000L)
        seedFinishedSet(trainingUuid, weightedUuid, weight = 100.0, reps = 5, finishedAt = 2_000L)
        seedFinishedSet(
            trainingUuid,
            secondExerciseUuid,
            weight = 70.0,
            reps = 4,
            finishedAt = 1_000L,
        )

        val records = repository.observePersonalRecordsBatch(
            uuidsByType = mapOf(
                weightedUuid.toString() to ExerciseTypeDataModel.WEIGHTED,
                secondExerciseUuid.toString() to ExerciseTypeDataModel.WEIGHTED,
            ),
        ).first()

        assertEquals(100.0, records[weightedUuid.toString()]?.weight)
        assertEquals(70.0, records[secondExerciseUuid.toString()]?.weight)
    }

    @Test
    fun `observePrSetUuids exposes the set uuids of every PR holder`() = runTest {
        val (trainingUuid, exerciseUuid) = seedTrainingAndExercise()
        val prSetUuid = Uuid.random()
        seedFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            weight = 80.0,
            reps = 5,
            finishedAt = 1_000L,
            setUuid = Uuid.random(),
        )
        seedFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            weight = 100.0,
            reps = 5,
            finishedAt = 2_000L,
            setUuid = prSetUuid,
        )

        val ids = repository.observePrSetUuids(
            uuidsByType = mapOf(exerciseUuid.toString() to ExerciseTypeDataModel.WEIGHTED),
        ).first()

        assertEquals(setOf(prSetUuid.toString()), ids)
    }

    private suspend fun seedTrainingAndExercise(
        type: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
    ): Pair<Uuid, Uuid> {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        env.trainingDao.insert(
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
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = "Bench-$exerciseUuid",
                type = type,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        return trainingUuid to exerciseUuid
    }

    private suspend fun seedFinishedSet(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        weight: Double?,
        reps: Int,
        finishedAt: Long,
        setUuid: Uuid = Uuid.random(),
    ) {
        val sessionUuid = Uuid.random()
        env.sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        val performedUuid = Uuid.random()
        env.performedExerciseDao.insert(
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
        env.setDao.insert(
            SetEntity(
                uuid = setUuid,
                performedExerciseUuid = performedUuid,
                position = 0,
                reps = reps,
                weight = weight,
                type = SetTypeEntity.WORK,
            ),
        )
    }
}
