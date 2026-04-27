// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.session

import io.github.stslex.workeeper.core.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.database.session.model.SetEntity
import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.database.training.TrainingEntity
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
internal class SessionDaoTest : BaseDatabaseTest() {

    private val sessionDao
        get() = database.sessionDao
    private val performedExerciseDao
        get() = database.performedExerciseDao
    private val setDao
        get() = database.setDao
    private val trainingDao
        get() = database.trainingDao
    private val exerciseDao
        get() = database.exerciseDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `getRecentSessionsForExercise returns finished sessions ordered by finished_at desc`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isAdhoc = false)

        val olderSession = insertFinishedSessionWithSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
        )
        val newerSession = insertFinishedSessionWithSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 2_000L,
        )

        val result = sessionDao.getRecentSessionsForExercise(exerciseUuid, limit = 10)

        assertEquals(listOf(newerSession, olderSession), result.map { it.sessionUuid })
        result.forEach { assertEquals("Push Day", it.trainingName) }
    }

    @Test
    fun `getRecentSessionsForExercise excludes sessions without sets`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isAdhoc = false)

        val withSet = insertFinishedSessionWithSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
        )
        // Skipped/empty session: we still create the performed_exercise row but no sets.
        val skippedSessionUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = skippedSessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_500L,
            ),
        )
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = Uuid.random(),
                    sessionUuid = skippedSessionUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = true,
                ),
            ),
        )

        val result = sessionDao.getRecentSessionsForExercise(exerciseUuid, limit = 10)

        assertEquals(listOf(withSet), result.map { it.sessionUuid })
    }

    @Test
    fun `getRecentSessionsForExercise excludes in-progress sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isAdhoc = false)

        val inProgressUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = inProgressUuid,
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
                    sessionUuid = inProgressUuid,
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
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
        )

        val result = sessionDao.getRecentSessionsForExercise(exerciseUuid, limit = 10)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRecentSessionsForExercise honours limit`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isAdhoc = false)
        repeat(7) { idx ->
            insertFinishedSessionWithSet(
                trainingUuid = trainingUuid,
                exerciseUuid = exerciseUuid,
                finishedAt = (idx + 1) * 1_000L,
            )
        }
        val result = sessionDao.getRecentSessionsForExercise(exerciseUuid, limit = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun `getRecentSessionsForExercise marks adhoc sessions correctly`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid, isAdhoc = true)
        insertFinishedSessionWithSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
        )
        val row = sessionDao.getRecentSessionsForExercise(exerciseUuid, limit = 5).single()
        assertEquals(true, row.isAdhoc)
    }

    private suspend fun seedTrainingAndExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        isAdhoc: Boolean,
    ) {
        trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push Day",
                description = null,
                isAdhoc = isAdhoc,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
    }

    private suspend fun insertFinishedSessionWithSet(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        finishedAt: Long,
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
                reps = 5,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
        )
        return sessionUuid
    }
}
