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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SessionDaoRecentWithStatsTest : BaseDatabaseTest() {

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
    fun `observeRecentWithStats returns newest-first rows with counts for mixed sessions`() = runTest {
        val templateTrainingUuid = Uuid.random()
        val adhocTrainingUuid = Uuid.random()
        val benchUuid = Uuid.random()
        val flyUuid = Uuid.random()
        seedTraining(
            uuid = templateTrainingUuid,
            name = "Push Day",
            isAdhoc = false,
        )
        seedTraining(
            uuid = adhocTrainingUuid,
            name = "Quick Session",
            isAdhoc = true,
        )
        seedExercise(uuid = benchUuid, name = "Bench")
        seedExercise(uuid = flyUuid, name = "Fly")

        val newestSessionUuid = insertFinishedSession(
            trainingUuid = templateTrainingUuid,
            finishedAt = 3_000L,
            performedRows = listOf(
                performedRow(
                    sessionUuid = Uuid.random(),
                    exerciseUuid = benchUuid,
                    position = 0,
                    skipped = false,
                ),
                performedRow(
                    sessionUuid = Uuid.random(),
                    exerciseUuid = flyUuid,
                    position = 1,
                    skipped = true,
                ),
            ),
            sessionUuid = Uuid.random(),
        )
        val newestPerformedRows = performedBySession(newestSessionUuid, benchUuid, flyUuid)
        setDao.insert(
            SetEntity(
                performedExerciseUuid = newestPerformedRows.first().uuid,
                position = 0,
                reps = 5,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
        )
        setDao.insert(
            SetEntity(
                performedExerciseUuid = newestPerformedRows.first().uuid,
                position = 1,
                reps = 6,
                weight = 105.0,
                type = SetTypeEntity.WORK,
            ),
        )

        val middleSessionUuid = insertFinishedSession(
            trainingUuid = adhocTrainingUuid,
            finishedAt = 2_000L,
            performedRows = listOf(
                PerformedExerciseEntity(
                    sessionUuid = Uuid.random(),
                    exerciseUuid = benchUuid,
                    position = 0,
                    skipped = false,
                ),
            ),
            sessionUuid = Uuid.random(),
        )
        val oldestSessionUuid = insertFinishedSession(
            trainingUuid = templateTrainingUuid,
            finishedAt = 1_000L,
            performedRows = listOf(
                PerformedExerciseEntity(
                    sessionUuid = Uuid.random(),
                    exerciseUuid = flyUuid,
                    position = 0,
                    skipped = true,
                ),
            ),
            sessionUuid = Uuid.random(),
        )

        val rows = sessionDao.observeRecentWithStats(limit = 10).first()

        assertEquals(
            listOf(newestSessionUuid, middleSessionUuid, oldestSessionUuid),
            rows.map { it.sessionUuid },
        )
        assertEquals("Push Day", rows[0].trainingName)
        assertEquals(false, rows[0].isAdhoc)
        assertEquals(1, rows[0].exerciseCount)
        assertEquals(2, rows[0].setCount)

        assertEquals("Quick Session", rows[1].trainingName)
        assertEquals(true, rows[1].isAdhoc)
        assertEquals(1, rows[1].exerciseCount)
        assertEquals(0, rows[1].setCount)

        assertEquals("Push Day", rows[2].trainingName)
        assertEquals(false, rows[2].isAdhoc)
        assertEquals(0, rows[2].exerciseCount)
        assertEquals(0, rows[2].setCount)
    }

    @Test
    fun `observeRecentWithStats respects limit`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(uuid = trainingUuid, name = "Push Day", isAdhoc = false)
        seedExercise(uuid = exerciseUuid, name = "Bench")

        repeat(4) { index ->
            insertFinishedSession(
                trainingUuid = trainingUuid,
                finishedAt = (index + 1) * 1_000L,
                performedRows = listOf(
                    PerformedExerciseEntity(
                        sessionUuid = Uuid.random(),
                        exerciseUuid = exerciseUuid,
                        position = 0,
                        skipped = false,
                    ),
                ),
                sessionUuid = Uuid.random(),
            )
        }

        val rows = sessionDao.observeRecentWithStats(limit = 2).first()

        assertEquals(2, rows.size)
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

    private suspend fun seedExercise(uuid: Uuid, name: String) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
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
    }

    private suspend fun insertFinishedSession(
        trainingUuid: Uuid,
        finishedAt: Long,
        performedRows: List<PerformedExerciseEntity>,
        sessionUuid: Uuid,
    ): Uuid {
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        performedExerciseDao.insert(
            performedRows.map { row -> row.copy(sessionUuid = sessionUuid) },
        )
        return sessionUuid
    }

    private suspend fun performedBySession(
        sessionUuid: Uuid,
        firstExerciseUuid: Uuid,
        secondExerciseUuid: Uuid,
    ): List<PerformedExerciseEntity> = performedExerciseDao.getBySession(sessionUuid)
        .sortedBy { row ->
            when (row.exerciseUuid) {
                firstExerciseUuid -> 0
                secondExerciseUuid -> 1
                else -> 2
            }
        }

    private fun performedRow(
        sessionUuid: Uuid,
        exerciseUuid: Uuid,
        position: Int,
        skipped: Boolean,
    ) = PerformedExerciseEntity(
        sessionUuid = sessionUuid,
        exerciseUuid = exerciseUuid,
        position = position,
        skipped = skipped,
    )
}
