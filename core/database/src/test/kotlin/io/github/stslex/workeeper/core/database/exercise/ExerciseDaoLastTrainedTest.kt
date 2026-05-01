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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class ExerciseDaoLastTrainedTest : BaseDatabaseTest() {

    private val exerciseDao get() = database.exerciseDao
    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val trainingDao get() = database.trainingDao

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    fun `empty db returns null`() = runTest {
        assertNull(exerciseDao.getLastTrainedExerciseUuid())
    }

    @Test
    fun `only in-progress sessions returns null`() = runTest {
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

        assertNull(exerciseDao.getLastTrainedExerciseUuid())
    }

    @Test
    fun `single finished session returns its exercise`() = runTest {
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

        assertEquals(exerciseUuid, exerciseDao.getLastTrainedExerciseUuid())
    }

    @Test
    fun `multiple finished sessions returns most recently finished`() = runTest {
        val trainingUuid = Uuid.random()
        val older = Uuid.random()
        val newer = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(older, "Bench")
        seedExercise(newer, "Squat")
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = older,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = newer,
            state = SessionStateEntity.FINISHED,
            finishedAt = 5_000L,
        )

        assertEquals(newer, exerciseDao.getLastTrainedExerciseUuid())
    }

    @Test
    fun `newer in-progress does not outrank older finished session`() = runTest {
        val trainingUuid = Uuid.random()
        val finishedExercise = Uuid.random()
        val inProgressExercise = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(finishedExercise, "Bench")
        seedExercise(inProgressExercise, "Squat")
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = finishedExercise,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        seedSessionWithExercise(
            trainingUuid = trainingUuid,
            exerciseUuid = inProgressExercise,
            state = SessionStateEntity.IN_PROGRESS,
            finishedAt = null,
        )

        assertEquals(finishedExercise, exerciseDao.getLastTrainedExerciseUuid())
    }

    @Test
    fun `archived exercise still wins if its session is most recent`() = runTest {
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

        assertEquals(archived, exerciseDao.getLastTrainedExerciseUuid())
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

    private suspend fun seedExercise(uuid: Uuid, name: String, archived: Boolean = false) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
                type = ExerciseTypeEntity.WEIGHTED,
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
