// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionStateDataModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class SessionRepositoryImplReadDbTest {

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
    fun `observeActive emits null when no in-progress session exists`() = runTest {
        assertNull(repository.observeActive().first())
    }

    @Test
    fun `observeActive emits the in-progress session and ignores finished ones`() = runTest {
        val training = env.seedTraining()
        env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 100L,
            finishedAt = 500L,
        )
        val active = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1_000L,
        )

        val emitted = repository.observeActive().first()

        assertNotNull(emitted)
        assertEquals(active.uuid.toString(), emitted?.uuid)
        assertEquals(SessionStateDataModel.IN_PROGRESS, emitted?.state)
        assertEquals(1_000L, emitted?.startedAt)
        assertNull(emitted?.finishedAt)
    }

    @Test
    fun `observeAnyActiveSession emits the active session info or null`() = runTest {
        assertNull(repository.observeAnyActiveSession().first())

        val training = env.seedTraining()
        val active = env.seedSession(trainingUuid = training.uuid, startedAt = 7L)

        val emitted = repository.observeAnyActiveSession().first()
        assertNotNull(emitted)
        assertEquals(active.uuid.toString(), emitted?.sessionUuid)
        assertEquals(training.uuid.toString(), emitted?.trainingUuid)
        assertEquals(7L, emitted?.startedAt)
    }

    @Test
    fun `observeActiveSessionWithStats counts performed exercises and done count`() = runTest {
        val training = env.seedTraining(name = "Push Day", isAdhoc = false)
        val firstExercise = env.seedExercise(name = "Bench")
        val secondExercise = env.seedExercise(name = "Incline")
        val session = env.seedSession(trainingUuid = training.uuid, startedAt = 42L)
        val firstPerformed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = firstExercise.uuid,
            position = 0,
        )
        // Skipped row should NOT count toward total or done.
        env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = secondExercise.uuid,
            position = 1,
            skipped = true,
        )
        env.seedSet(performedExerciseUuid = firstPerformed.uuid)

        val emitted = repository.observeActiveSessionWithStats().first()

        assertNotNull(emitted)
        assertEquals(session.uuid.toString(), emitted?.sessionUuid)
        assertEquals(training.uuid.toString(), emitted?.trainingUuid)
        assertEquals("Push Day", emitted?.trainingName)
        assertEquals(false, emitted?.isAdhoc)
        assertEquals(42L, emitted?.startedAt)
        assertEquals(1, emitted?.totalCount)
        assertEquals(1, emitted?.doneCount)
    }

    @Test
    fun `getAnyActiveSession returns active session or null`() = runTest {
        assertNull(repository.getAnyActiveSession())

        val training = env.seedTraining()
        val active = env.seedSession(trainingUuid = training.uuid, startedAt = 99L)

        val result = repository.getAnyActiveSession()
        assertNotNull(result)
        assertEquals(active.uuid.toString(), result?.sessionUuid)
        assertEquals(training.uuid.toString(), result?.trainingUuid)
        assertEquals(99L, result?.startedAt)
    }

    @Test
    fun `getActive returns full session model when present and null when absent`() = runTest {
        assertNull(repository.getActive())

        val training = env.seedTraining()
        val active = env.seedSession(trainingUuid = training.uuid, startedAt = 11L)

        val result = repository.getActive()
        assertNotNull(result)
        assertEquals(active.uuid.toString(), result?.uuid)
        assertEquals(SessionStateDataModel.IN_PROGRESS, result?.state)
        assertEquals(11L, result?.startedAt)
    }

    @Test
    fun `observeRecent returns finished sessions ordered desc by finished_at and respects limit`() =
        runTest {
            val training = env.seedTraining()
            val older = env.seedSession(
                trainingUuid = training.uuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_000L,
            )
            val middle = env.seedSession(
                trainingUuid = training.uuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 2_000L,
            )
            val newest = env.seedSession(
                trainingUuid = training.uuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 3_000L,
            )
            // In-progress sessions must NOT show up in observeRecent.
            env.seedSession(trainingUuid = training.uuid)

            val emitted = repository.observeRecent(limit = 2).first()

            assertEquals(2, emitted.size)
            assertEquals(newest.uuid.toString(), emitted[0].uuid)
            assertEquals(middle.uuid.toString(), emitted[1].uuid)
            assertTrue(emitted.none { it.uuid == older.uuid.toString() })
        }

    @Test
    fun `observeRecentWithStats returns finished sessions with exercise and set counts`() = runTest {
        val training = env.seedTraining(name = "Push Day", isAdhoc = false)
        val exerciseA = env.seedExercise(name = "Bench")
        val exerciseB = env.seedExercise(name = "Pull")
        val session = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 1_000L,
            finishedAt = 2_000L,
        )
        val performedA = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = exerciseA.uuid,
            position = 0,
        )
        env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = exerciseB.uuid,
            position = 1,
            skipped = true,
        )
        env.seedSet(performedExerciseUuid = performedA.uuid, position = 0)
        env.seedSet(performedExerciseUuid = performedA.uuid, position = 1)

        val emitted = repository.observeRecentWithStats(limit = 5).first()

        assertEquals(1, emitted.size)
        val row = emitted.single()
        assertEquals(session.uuid.toString(), row.sessionUuid)
        assertEquals("Push Day", row.trainingName)
        assertEquals(false, row.isAdhoc)
        assertEquals(2_000L, row.finishedAt)
        // Skipped performed_exercise row excluded.
        assertEquals(1, row.exerciseCount)
        // Both sets land under the non-skipped performed exercise.
        assertEquals(2, row.setCount)
    }

    @Test
    fun `getById returns mapped session or null`() = runTest {
        assertNull(repository.getById(Uuid.random().toString()))

        val training = env.seedTraining()
        val session = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 1L,
            finishedAt = 2L,
        )

        val result = repository.getById(session.uuid.toString())

        assertNotNull(result)
        assertEquals(session.uuid.toString(), result?.uuid)
        assertEquals(training.uuid.toString(), result?.trainingUuid)
        assertEquals(SessionStateDataModel.FINISHED, result?.state)
        assertEquals(2L, result?.finishedAt)
    }

    @Test
    fun `getRecentFinishedByTraining returns only finished sessions for that training`() = runTest {
        val target = env.seedTraining(name = "Target")
        val other = env.seedTraining(name = "Other")
        val older = env.seedSession(
            trainingUuid = target.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        val newer = env.seedSession(
            trainingUuid = target.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 2_000L,
        )
        env.seedSession(
            trainingUuid = other.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 3_000L,
        )
        env.seedSession(trainingUuid = target.uuid)

        val result = repository.getRecentFinishedByTraining(target.uuid.toString(), limit = 5)

        assertEquals(listOf(newer.uuid.toString(), older.uuid.toString()), result.map { it.uuid })
    }

    @Test
    fun `getSessionDetail returns null for unknown session and for in-progress session`() = runTest {
        assertNull(repository.getSessionDetail(Uuid.random().toString()))

        val training = env.seedTraining()
        val inProgress = env.seedSession(trainingUuid = training.uuid)

        // In-progress sessions have finishedAt = null. The repository contract returns null.
        assertNull(repository.getSessionDetail(inProgress.uuid.toString()))
    }

    @Test
    fun `getSessionDetail assembles full hierarchy from real DB`() = runTest {
        val training = env.seedTraining(name = "Push Day", isAdhoc = false)
        val benchExercise = env.seedExercise(name = "Bench", type = ExerciseTypeEntity.WEIGHTED)
        val pullExercise = env.seedExercise(
            name = "Pull Up",
            type = ExerciseTypeEntity.WEIGHTLESS,
        )
        val session = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 1_000L,
            finishedAt = 4_000L,
        )
        // Insert exercises out of position order to verify sort.
        val pullPerformed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = pullExercise.uuid,
            position = 1,
        )
        val benchPerformed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = benchExercise.uuid,
            position = 0,
        )
        // Bench: two sets, inserted out of order.
        env.seedSet(
            performedExerciseUuid = benchPerformed.uuid,
            position = 1,
            weight = 105.0,
            reps = 8,
            type = SetTypeEntity.FAIL,
        )
        env.seedSet(
            performedExerciseUuid = benchPerformed.uuid,
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeEntity.WORK,
        )
        env.seedSet(
            performedExerciseUuid = pullPerformed.uuid,
            position = 0,
            weight = null,
            reps = 12,
            type = SetTypeEntity.WORK,
        )

        val result = repository.getSessionDetail(session.uuid.toString())

        requireNotNull(result)
        assertEquals(session.uuid.toString(), result.sessionUuid)
        assertEquals(training.uuid.toString(), result.trainingUuid)
        assertEquals("Push Day", result.trainingName)
        assertEquals(false, result.isAdhoc)
        assertEquals(1_000L, result.startedAt)
        assertEquals(4_000L, result.finishedAt)
        // Performed exercises sorted by position.
        assertEquals(listOf("Bench", "Pull Up"), result.exercises.map { it.exerciseName })
        assertEquals(
            listOf(ExerciseTypeDataModel.WEIGHTED, ExerciseTypeDataModel.WEIGHTLESS),
            result.exercises.map { it.exerciseType },
        )
        // Sets sorted by position within each exercise.
        val benchSets = result.exercises.first { it.exerciseName == "Bench" }.sets
        assertEquals(listOf(0, 1), benchSets.indices.toList())
        assertEquals(listOf(100.0, 105.0), benchSets.map { it.weight })
        assertEquals(listOf(5, 8), benchSets.map { it.reps })
        assertEquals(
            listOf(SetsDataType.WORK, SetsDataType.FAIL),
            benchSets.map { it.type },
        )
    }

    @Test
    fun `getHistoryByExercise groups rows per session and includes set summaries`() = runTest {
        val training = env.seedTraining(name = "Push", isAdhoc = false)
        val exercise = env.seedExercise(name = "Bench")
        // A finished session with two sets — should collapse into one HistoryEntry with two
        // SetSummary items.
        val firstSession = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 0L,
            finishedAt = 1_000L,
        )
        val firstPerformed = env.seedPerformed(
            sessionUuid = firstSession.uuid,
            exerciseUuid = exercise.uuid,
        )
        env.seedSet(performedExerciseUuid = firstPerformed.uuid, position = 0, weight = 100.0, reps = 5)
        env.seedSet(performedExerciseUuid = firstPerformed.uuid, position = 1, weight = 105.0, reps = 4)
        val secondSession = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 0L,
            finishedAt = 2_000L,
        )
        val secondPerformed = env.seedPerformed(
            sessionUuid = secondSession.uuid,
            exerciseUuid = exercise.uuid,
        )
        env.seedSet(performedExerciseUuid = secondPerformed.uuid, position = 0, weight = 110.0, reps = 5)

        val result = repository.getHistoryByExercise(exercise.uuid.toString())

        // Newest first.
        assertEquals(2, result.size)
        assertEquals(secondSession.uuid.toString(), result[0].sessionUuid)
        assertEquals(firstSession.uuid.toString(), result[1].sessionUuid)
        // Multiple sets collapse into one entry's `sets` list.
        assertEquals(2, result[1].sets.size)
        assertEquals(listOf(100.0, 105.0), result[1].sets.map { it.weight })
        assertEquals(1, result[0].sets.size)
        assertEquals(110.0, result[0].sets.first().weight)
    }

    @Test
    fun `pagedFinished emits finished sessions newest first`() = runTest {
        val training = env.seedTraining()
        val older = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        val newer = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 2_000L,
        )
        env.seedSession(trainingUuid = training.uuid)

        val snapshot = repository.pagedFinished().asSnapshot()

        assertEquals(
            listOf(newer.uuid.toString(), older.uuid.toString()),
            snapshot.map { it.uuid },
        )
    }

    @Test
    fun `pagedFinishedByTraining filters to the requested training`() = runTest {
        val target = env.seedTraining(name = "Target")
        val other = env.seedTraining(name = "Other")
        val mine = env.seedSession(
            trainingUuid = target.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        env.seedSession(
            trainingUuid = other.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 2_000L,
        )

        val snapshot = repository.pagedFinishedByTraining(target.uuid.toString()).asSnapshot()

        assertEquals(listOf(mine.uuid.toString()), snapshot.map { it.uuid })
    }

    @Test
    fun `pagedHistoryByExercise emits one entry per set across finished sessions`() = runTest {
        val training = env.seedTraining(name = "Push", isAdhoc = false)
        val exercise = env.seedExercise(name = "Bench")
        val session = env.seedSession(
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = 1_000L,
        )
        val performed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = exercise.uuid,
        )
        env.seedSet(performedExerciseUuid = performed.uuid, position = 0, weight = 100.0, reps = 5)
        env.seedSet(performedExerciseUuid = performed.uuid, position = 1, weight = 110.0, reps = 4)

        val snapshot = repository.pagedHistoryByExercise(exercise.uuid.toString()).asSnapshot()

        // Each row in the paged source becomes a single-set HistoryEntry per the doc-comment.
        assertEquals(2, snapshot.size)
        assertEquals(
            listOf(100.0, 110.0),
            snapshot.mapNotNull { it.sets.single().weight }.sorted(),
        )
        assertTrue(snapshot.all { it.sessionUuid == session.uuid.toString() })
    }

    @Suppress("unused")
    private suspend fun PagingSource<Int, *>.loadAll(): List<*> = (
        load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page
        ).data
}
