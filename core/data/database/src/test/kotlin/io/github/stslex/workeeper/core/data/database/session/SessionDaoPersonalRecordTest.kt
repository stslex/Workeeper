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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SessionDaoPersonalRecordTest : BaseDatabaseTest() {

    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val setDao get() = database.setDao
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
    fun `getPersonalRecord returns null when no finished session logged the exercise`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)

        val result = sessionDao.getPersonalRecord(exerciseUuid, isWeightless = false)

        assertNull(result)
    }

    @Test
    fun `getPersonalRecord weighted picks heaviest weight then most reps then earliest finish`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        // Light first set is logged earliest.
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = 80.0,
            reps = 5,
        )
        // Heaviest weight at 100kg, but logged the latest — should still win on weight.
        val heaviestSession = insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 5_000L,
            weight = 100.0,
            reps = 5,
        )
        // Tied weight + tied reps later — earliest-wins tiebreak should NOT pick this.
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 6_000L,
            weight = 100.0,
            reps = 5,
        )
        // Tied weight, lower reps — should NOT win.
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 4_500L,
            weight = 100.0,
            reps = 4,
        )

        val result = sessionDao.getPersonalRecord(exerciseUuid, isWeightless = false)

        assertEquals(100.0, result?.weight)
        assertEquals(5, result?.reps)
        assertEquals(heaviestSession, result?.sessionUuid)
        assertEquals(5_000L, result?.finishedAt)
    }

    @Test
    fun `getPersonalRecord weighted ignores in-progress sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = 60.0,
            reps = 8,
        )
        // In-progress sets must not contribute to PR even if heavier.
        val inProgressSession = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = inProgressSession,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            ),
        )
        val performedUuid = Uuid.random()
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = performedUuid,
                    sessionUuid = inProgressSession,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = false,
                ),
            ),
        )
        setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = performedUuid,
                position = 0,
                reps = 5,
                weight = 200.0,
                type = SetTypeEntity.WORK,
            ),
        )

        val result = sessionDao.getPersonalRecord(exerciseUuid, isWeightless = false)

        assertEquals(60.0, result?.weight)
        assertEquals(8, result?.reps)
    }

    @Test
    fun `getPersonalRecord weightless picks max reps`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isWeightless = true)
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = null,
            reps = 12,
        )
        val recordSession = insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 3_000L,
            weight = null,
            reps = 15,
        )

        val result = sessionDao.getPersonalRecord(exerciseUuid, isWeightless = true)

        assertEquals(15, result?.reps)
        assertEquals(recordSession, result?.sessionUuid)
    }

    @Test
    fun `getPersonalRecord weightless tie on reps picks earliest finish`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isWeightless = true)
        val earliest = insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = null,
            reps = 12,
        )
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 2_000L,
            weight = null,
            reps = 12,
        )

        val result = sessionDao.getPersonalRecord(exerciseUuid, isWeightless = true)

        assertEquals(earliest, result?.sessionUuid)
        assertEquals(1_000L, result?.finishedAt)
    }

    private suspend fun seedTrainingAndExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        isWeightless: Boolean = false,
    ) {
        trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push Day",
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
                name = "Bench",
                type = if (isWeightless) ExerciseTypeEntity.WEIGHTLESS else ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
    }

    private suspend fun insertFinishedSet(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        finishedAt: Long,
        weight: Double?,
        reps: Int,
    ): Uuid {
        val sessionUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        val performedUuid = Uuid.random()
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
        setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = performedUuid,
                position = 0,
                reps = reps,
                weight = weight,
                type = SetTypeEntity.WORK,
            ),
        )
        return sessionUuid
    }
}
