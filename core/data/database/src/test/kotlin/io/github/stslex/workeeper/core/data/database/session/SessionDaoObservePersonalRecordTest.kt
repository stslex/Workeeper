// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.flow.first
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
internal class SessionDaoObservePersonalRecordTest : BaseDatabaseTest() {

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
    fun `observePersonalRecord emits null when no finished session exists`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)

        val result = sessionDao.observePersonalRecord(exerciseUuid, isWeightless = false).first()

        assertNull(result)
    }

    @Test
    fun `observePersonalRecord emits the heaviest set for weighted exercise`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = 80.0,
            reps = 5,
        )
        val heaviest = insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 5_000L,
            weight = 100.0,
            reps = 5,
        )

        val result = sessionDao.observePersonalRecord(exerciseUuid, isWeightless = false).first()

        assertEquals(100.0, result?.weight)
        assertEquals(5, result?.reps)
        assertEquals(heaviest, result?.sessionUuid)
    }

    @Test
    fun `observePersonalRecord emits new value after a heavier set is logged`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = 80.0,
            reps = 5,
        )

        val initial = sessionDao.observePersonalRecord(exerciseUuid, isWeightless = false).first()
        assertEquals(80.0, initial?.weight)

        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 2_000L,
            weight = 110.0,
            reps = 5,
        )

        val updated = sessionDao.observePersonalRecord(exerciseUuid, isWeightless = false).first()
        assertEquals(110.0, updated?.weight)
    }

    @Test
    fun `observePersonalRecord weightless emits max reps`() = runTest {
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
        insertFinishedSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 2_000L,
            weight = null,
            reps = 18,
        )

        val result = sessionDao.observePersonalRecord(exerciseUuid, isWeightless = true).first()

        assertEquals(18, result?.reps)
    }

    @Test
    fun `observePersonalRecord ignores in-progress sessions`() = runTest {
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

        val result = sessionDao.observePersonalRecord(exerciseUuid, isWeightless = false).first()

        assertEquals(60.0, result?.weight)
        assertEquals(8, result?.reps)
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
