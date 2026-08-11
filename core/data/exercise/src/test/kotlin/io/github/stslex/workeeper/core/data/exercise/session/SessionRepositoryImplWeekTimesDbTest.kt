// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SessionRepositoryImplWeekTimesDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: SessionRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = SessionRepositoryImpl(
            dao = env.sessionDao,
            performedExerciseDao = env.performedExerciseDao,
            setDao = env.setDao,
            trainingDao = env.trainingDao,
            exerciseDao = env.exerciseDao,
            trainingExerciseDao = env.trainingExerciseDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `observeFinishedTimesBetween returns only finished timestamps inside the half-open range`() =
        runTest {
            val training = env.seedTraining()
            // Inside the range, finished — the only rows that may surface.
            env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 100L)
            env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 250L)
            // On the start bound — inclusive.
            env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 50L)
            // On the end bound — exclusive.
            env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 300L)
            // Below the range.
            env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 49L)
            // In progress — the banner's row, never the readout's.
            env.seedSession(training.uuid, state = SessionStateEntity.IN_PROGRESS, finishedAt = null)

            val times = repository.observeFinishedTimesBetween(
                startInclusive = 50L,
                endExclusive = 300L,
            ).first()

            assertEquals(listOf(50L, 100L, 250L), times.sorted())
        }

    @Test
    fun `observeFinishedTimesBetween drops a FINISHED row with a null timestamp`() = runTest {
        val training = env.seedTraining()
        env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = null)
        env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 10L)

        val times = repository.observeFinishedTimesBetween(
            startInclusive = 0L,
            endExclusive = 100L,
        ).first()

        assertEquals(listOf(10L), times)
    }

    @Test
    fun `observeFinishedTimesBetween is empty when nothing finished in the range`() = runTest {
        val training = env.seedTraining()
        env.seedSession(training.uuid, state = SessionStateEntity.FINISHED, finishedAt = 5_000L)

        val times = repository.observeFinishedTimesBetween(
            startInclusive = 0L,
            endExclusive = 100L,
        ).first()

        assertEquals(emptyList<Long>(), times)
    }
}
