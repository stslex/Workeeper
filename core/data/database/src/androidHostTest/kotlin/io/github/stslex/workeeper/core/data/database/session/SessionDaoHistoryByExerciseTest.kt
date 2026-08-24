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
internal class SessionDaoHistoryByExerciseTest : BaseDatabaseTest() {

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
    fun `getHistoryByExercise orders rows by finished_at desc then position asc`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid, "Push Day", isAdhoc = false)
        seedExercise(exerciseUuid)

        // Older session, two sets — newer one renders first per finished_at DESC.
        val older = insertFinishedSessionWithSets(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            sets = listOf(
                SetSpec(position = 1, weight = 100.0, reps = 5),
                SetSpec(position = 0, weight = 100.0, reps = 5),
            ),
        )
        val newer = insertFinishedSessionWithSets(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 2_000L,
            sets = listOf(
                SetSpec(position = 0, weight = 100.0, reps = 5),
            ),
        )

        val rows = sessionDao.getHistoryByExercise(exerciseUuid)

        assertEquals(3, rows.size)
        // First two rows belong to newer session, then older's sets in position order.
        assertEquals(newer, rows[0].sessionUuid)
        assertEquals(0, rows[0].position)
        assertEquals(older, rows[1].sessionUuid)
        assertEquals(0, rows[1].position)
        assertEquals(older, rows[2].sessionUuid)
        assertEquals(1, rows[2].position)
    }

    @Test
    fun `getHistoryByExercise includes ad-hoc trainings`() = runTest {
        val adhocTraining = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(adhocTraining, "Track now", isAdhoc = true)
        seedExercise(exerciseUuid)
        insertFinishedSessionWithSets(
            trainingUuid = adhocTraining,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(position = 0, weight = 80.0, reps = 5)),
        )

        val rows = sessionDao.getHistoryByExercise(exerciseUuid)

        assertEquals(1, rows.size)
        assertTrue(rows.first().isAdhoc)
        assertEquals("Track now", rows.first().trainingName)
    }

    @Test
    fun `getHistoryByExercise excludes in-progress sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid, "Push Day", isAdhoc = false)
        seedExercise(exerciseUuid)
        val inProgress = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = inProgress,
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
                    sessionUuid = inProgress,
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

        val rows = sessionDao.getHistoryByExercise(exerciseUuid)

        assertTrue(rows.isEmpty())
    }

    private suspend fun seedTraining(uuid: Uuid, name: String, isAdhoc: Boolean) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = name,
                description = null,
                isAdhoc = isAdhoc,
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

    private data class SetSpec(val position: Int, val weight: Double?, val reps: Int)

    private suspend fun insertFinishedSessionWithSets(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        finishedAt: Long,
        sets: List<SetSpec>,
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
        sets.forEach { spec ->
            setDao.insert(
                SetEntity(
                    uuid = Uuid.random(),
                    performedExerciseUuid = performedUuid,
                    position = spec.position,
                    reps = spec.reps,
                    weight = spec.weight,
                    type = SetTypeEntity.WORK,
                ),
            )
        }
        return sessionUuid
    }
}
