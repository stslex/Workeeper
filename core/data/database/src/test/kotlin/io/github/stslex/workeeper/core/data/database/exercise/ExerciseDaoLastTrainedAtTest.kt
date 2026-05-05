// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
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

@Suppress("MagicNumber")
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class ExerciseDaoLastTrainedAtTest : BaseDatabaseTest() {

    private val exerciseDao get() = database.exerciseDao
    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val trainingDao get() = database.trainingDao

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    fun `exercise with no sessions returns null`() = runTest {
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid)

        val result = exerciseDao.observeLastTrainedAt(exerciseUuid).first()

        assertNull(result)
    }

    @Test
    fun `in-progress sessions are excluded`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid)
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.IN_PROGRESS,
            finishedAt = null,
        )

        val result = exerciseDao.observeLastTrainedAt(exerciseUuid).first()

        assertNull(result)
    }

    @Test
    fun `single finished session returns its finishedAt`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid)
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 5_000L,
        )

        val result = exerciseDao.observeLastTrainedAt(exerciseUuid).first()

        assertEquals(5_000L, result)
    }

    @Test
    fun `multiple finished sessions return MAX(finishedAt)`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid)
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
            finishedAt = 9_000L,
        )
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 5_000L,
        )

        val result = exerciseDao.observeLastTrainedAt(exerciseUuid).first()

        assertEquals(9_000L, result)
    }

    @Test
    fun `skipped performed-exercise rows are excluded`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid)
        // Older finished session, not skipped
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
            skipped = false,
        )
        // Newer finished session, but the exercise was skipped — must not count.
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 9_000L,
            skipped = true,
        )

        val result = exerciseDao.observeLastTrainedAt(exerciseUuid).first()

        assertEquals(1_000L, result)
    }

    @Test
    fun `sessions for other exercises are not counted`() = runTest {
        val trainingUuid = Uuid.random()
        val target = Uuid.random()
        val other = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(target)
        seedExercise(other)
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = other,
            state = SessionStateEntity.FINISHED,
            finishedAt = 9_000L,
        )

        val result = exerciseDao.observeLastTrainedAt(target).first()

        assertNull(result)
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

    private suspend fun seedExercise(uuid: Uuid) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Exercise-$uuid",
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

    private suspend fun seedSessionWithExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        state: SessionStateEntity,
        finishedAt: Long?,
        skipped: Boolean = false,
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
                    skipped = skipped,
                ),
            ),
        )
    }
}
