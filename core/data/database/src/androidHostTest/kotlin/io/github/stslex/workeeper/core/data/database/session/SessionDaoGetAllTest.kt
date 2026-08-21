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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

/**
 * Covers the session-side unfiltered bulk readers used by the AI snapshot export:
 * [SessionDao.getAll] (must include IN_PROGRESS + FINISHED), [PerformedExerciseDao.getAll],
 * and [SetDao.getAll] (rows returned in position order).
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SessionDaoGetAllTest : BaseDatabaseTest() {

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
    fun `getAll returns both in-progress and finished sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        val active = session(trainingUuid, SessionStateEntity.IN_PROGRESS, finishedAt = null)
        val finished = session(trainingUuid, SessionStateEntity.FINISHED, finishedAt = 100L)
        listOf(active, finished).forEach { sessionDao.insert(it) }

        val all = sessionDao.getAll()

        assertEquals(setOf(active.uuid, finished.uuid), all.map { it.uuid }.toSet())
    }

    @Test
    fun `performed-exercise and set getAll return the full graph in position order`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        val session = session(trainingUuid, SessionStateEntity.FINISHED, finishedAt = 100L)
        sessionDao.insert(session)
        val performed = PerformedExerciseEntity(
            sessionUuid = session.uuid,
            exerciseUuid = exerciseUuid,
            position = 0,
            skipped = false,
        )
        performedExerciseDao.insert(performed)
        setDao.insert(
            SetEntity(
                performedExerciseUuid = performed.uuid,
                position = 1,
                reps = 8,
                weight = 50.0,
                type = SetTypeEntity.WORK,
            ),
        )
        setDao.insert(
            SetEntity(
                performedExerciseUuid = performed.uuid,
                position = 0,
                reps = 5,
                weight = null,
                type = SetTypeEntity.WARM,
            ),
        )

        assertEquals(listOf(performed.uuid), performedExerciseDao.getAll().map { it.uuid })
        assertEquals(listOf(0, 1), setDao.getAll().map { it.position })
    }

    private suspend fun seedTrainingAndExercise(trainingUuid: Uuid, exerciseUuid: Uuid) {
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
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
                isAdhoc = false,
            ),
        )
    }

    private fun session(
        trainingUuid: Uuid,
        state: SessionStateEntity,
        finishedAt: Long?,
    ) = SessionEntity(
        trainingUuid = trainingUuid,
        state = state,
        startedAt = 0L,
        finishedAt = finishedAt,
    )
}
