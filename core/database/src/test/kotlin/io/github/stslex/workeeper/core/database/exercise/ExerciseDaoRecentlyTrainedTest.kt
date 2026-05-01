// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.exercise

import io.github.stslex.workeeper.core.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.database.session.SessionEntity
import io.github.stslex.workeeper.core.database.session.SessionStateEntity
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
internal class ExerciseDaoRecentlyTrainedTest : BaseDatabaseTest() {

    private val exerciseDao get() = database.exerciseDao
    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val trainingDao get() = database.trainingDao

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    fun `excludes exercises with no finished sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val touched = Uuid.random()
        val untouched = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(touched, "Bench")
        seedExercise(untouched, "Curl")
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = touched,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(touched, rows.first().uuid)
    }

    @Test
    fun `excludes archived exercises`() = runTest {
        val trainingUuid = Uuid.random()
        val active = Uuid.random()
        val archived = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(active, "Bench", archived = false)
        seedExercise(archived, "Squat", archived = true)
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = active,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = archived,
            state = SessionStateEntity.FINISHED,
            finishedAt = 5_000L,
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(active, rows.first().uuid)
    }

    @Test
    fun `excludes in-progress sessions when grouping`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid, "Bench")
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.IN_PROGRESS,
            finishedAt = null,
        )

        assertTrue(exerciseDao.getRecentlyTrainedExercises().isEmpty())
    }

    @Test
    fun `groups multiple sessions by max finished_at`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid, "Bench")
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 7_000L,
        )
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 3_000L,
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(7_000L, rows.first().lastFinishedAt)
    }

    @Test
    fun `orders DESC across multiple exercises`() = runTest {
        val trainingUuid = Uuid.random()
        val a = Uuid.random()
        val b = Uuid.random()
        val c = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(a, "A")
        seedExercise(b, "B")
        seedExercise(c, "C")
        seedSessionWithExercise(trainingUuid, a, SessionStateEntity.FINISHED, 1_000L)
        seedSessionWithExercise(trainingUuid, b, SessionStateEntity.FINISHED, 5_000L)
        seedSessionWithExercise(trainingUuid, c, SessionStateEntity.FINISHED, 3_000L)

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(listOf(b, c, a), rows.map { it.uuid })
        assertEquals(listOf(5_000L, 3_000L, 1_000L), rows.map { it.lastFinishedAt })
    }

    @Test
    fun `returns name and type`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid, "Pull-ups", type = ExerciseTypeEntity.WEIGHTLESS)
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )

        val row = exerciseDao.getRecentlyTrainedExercises().single()

        assertEquals("Pull-ups", row.name)
        assertEquals(ExerciseTypeEntity.WEIGHTLESS, row.type)
    }

    private suspend fun seedTraining(uuid: Uuid) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = "T",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
    }

    private suspend fun seedExercise(
        uuid: Uuid,
        name: String,
        archived: Boolean = false,
        type: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
    ) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
                type = type,
                description = null,
                imagePath = null,
                archived = archived,
                createdAt = 0L,
                archivedAt = if (archived) 0L else null,
                lastAdhocSets = null,
            ),
        )
    }

    private suspend fun seedSessionWithExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        state: SessionStateEntity,
        finishedAt: Long?,
    ) {
        val sessionUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = state,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = Uuid.random(),
                    sessionUuid = sessionUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = false,
                ),
            ),
        )
    }
}
