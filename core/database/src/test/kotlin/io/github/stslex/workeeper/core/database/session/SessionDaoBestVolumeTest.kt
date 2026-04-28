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
internal class SessionDaoBestVolumeTest : BaseDatabaseTest() {

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
    fun `getBestSessionVolumes returns sessions ordered by total volume desc`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)

        val low = insertFinishedSession(trainingUuid, finishedAt = 1_000L) {
            // 60 * 5 = 300
            insertSet(it, position = 0, weight = 60.0, reps = 5)
        }
        val high = insertFinishedSession(trainingUuid, finishedAt = 2_000L) {
            // 100 * 5 + 100 * 5 = 1000
            insertSet(it, position = 0, weight = 100.0, reps = 5)
            insertSet(it, position = 1, weight = 100.0, reps = 5)
        }
        val mid = insertFinishedSession(trainingUuid, finishedAt = 3_000L) {
            // 80 * 5 = 400
            insertSet(it, position = 0, weight = 80.0, reps = 5)
        }

        val results = sessionDao.getBestSessionVolumes(sinceMillis = 0L, limit = 5)

        assertEquals(listOf(high, mid, low), results.map { it.sessionUuid })
        assertEquals(1_000.0, results.first().volume)
    }

    @Test
    fun `getBestSessionVolumes excludes weightless exercises`() = runTest {
        val trainingUuid = Uuid.random()
        val weightedExercise = Uuid.random()
        val weightlessExercise = Uuid.random()
        seedTrainingAndExercise(trainingUuid, weightedExercise)
        exerciseDao.insert(
            ExerciseEntity(
                uuid = weightlessExercise,
                name = "Pullups",
                type = ExerciseTypeEntity.WEIGHTLESS,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        val sessionUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_000L,
            ),
        )
        // Weighted contributes — bench 100x5 = 500
        val benchPerformed = Uuid.random()
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = benchPerformed,
                    sessionUuid = sessionUuid,
                    exerciseUuid = weightedExercise,
                    position = 0,
                    skipped = false,
                ),
            ),
        )
        insertSet(benchPerformed, position = 0, weight = 100.0, reps = 5)
        // Weightless does NOT contribute — should be excluded by the type predicate.
        val pullPerformed = Uuid.random()
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = pullPerformed,
                    sessionUuid = sessionUuid,
                    exerciseUuid = weightlessExercise,
                    position = 1,
                    skipped = false,
                ),
            ),
        )
        // Weight is null on weightless so the SUM would be null even if included; this
        // also exercises the `s.weight IS NOT NULL` filter.
        setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = pullPerformed,
                position = 0,
                reps = 12,
                weight = null,
                type = SetTypeEntity.WORK,
            ),
        )

        val results = sessionDao.getBestSessionVolumes(sinceMillis = 0L, limit = 5)

        assertEquals(1, results.size)
        assertEquals(500.0, results.single().volume)
    }

    @Test
    fun `getBestSessionVolumes respects sinceMillis window`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
        insertFinishedSession(trainingUuid, finishedAt = 1_000L) {
            insertSet(it, position = 0, weight = 100.0, reps = 5)
        }
        val recent = insertFinishedSession(trainingUuid, finishedAt = 5_000L) {
            insertSet(it, position = 0, weight = 80.0, reps = 5)
        }

        val results = sessionDao.getBestSessionVolumes(sinceMillis = 2_000L, limit = 5)

        assertEquals(listOf(recent), results.map { it.sessionUuid })
    }

    @Test
    fun `getBestSessionVolumes excludes in-progress sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTrainingAndExercise(trainingUuid, exerciseUuid)
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
        insertSet(performedUuid, position = 0, weight = 100.0, reps = 5)

        val results = sessionDao.getBestSessionVolumes(sinceMillis = 0L, limit = 5)

        assertTrue(results.isEmpty())
    }

    private suspend fun seedTrainingAndExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
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
        block: suspend (performedExerciseUuid: Uuid) -> Unit,
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
        // Reuse the only weighted exercise we seeded for the per-test setup.
        val exerciseUuid = exerciseDao.getAllActive().first().uuid
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
        block(performedUuid)
        return sessionUuid
    }

    private suspend fun insertSet(
        performedExerciseUuid: Uuid,
        position: Int,
        weight: Double?,
        reps: Int,
    ) {
        setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = performedExerciseUuid,
                position = position,
                reps = reps,
                weight = weight,
                type = SetTypeEntity.WORK,
            ),
        )
    }
}
