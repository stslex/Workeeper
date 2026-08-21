// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.stats

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class StatsRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: StatsRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = StatsRepositoryImpl(
            sessionDao = env.sessionDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `getBestSessionVolumes returns sessions ordered by volume descending`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        env.exerciseDao.insert(
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
        val lowVolumeSession = seedSessionWithSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 1_000L,
            weight = 50.0,
            reps = 5,
        )
        val highVolumeSession = seedSessionWithSet(
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            finishedAt = 2_000L,
            weight = 100.0,
            reps = 5,
        )

        val volumes = repository.getBestSessionVolumes(sinceMillis = 0L, limit = 5)

        assertEquals(2, volumes.size)
        assertEquals(highVolumeSession.toString(), volumes[0].sessionUuid)
        assertEquals(lowVolumeSession.toString(), volumes[1].sessionUuid)
        assertEquals(500.0, volumes[0].volume)
        assertEquals(250.0, volumes[1].volume)
    }

    @Test
    fun `getBestSessionVolumes returns empty when no finished session is in the window`() = runTest {
        val volumes = repository.getBestSessionVolumes(sinceMillis = 0L, limit = 10)

        assertTrue(volumes.isEmpty())
    }

    private suspend fun seedSessionWithSet(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        finishedAt: Long,
        weight: Double,
        reps: Int,
    ): Uuid {
        val sessionUuid = Uuid.random()
        env.sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        val performedUuid = Uuid.random()
        env.performedExerciseDao.insert(
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
        env.setDao.insert(
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
