// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.session

import androidx.room.withTransaction
import io.github.stslex.workeeper.core.database.AppDatabase
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SessionEntity
import io.github.stslex.workeeper.core.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.session.model.SetEntity
import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SessionRepositoryGetDetailTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val database = mockk<AppDatabase>()
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val performedExerciseDao = mockk<PerformedExerciseDao>(relaxed = true)
    private val setDao = mockk<SetDao>(relaxed = true)
    private val trainingDao = mockk<TrainingDao>(relaxed = true)
    private val exerciseDao = mockk<ExerciseDao>(relaxed = true)
    private val trainingExerciseDao = mockk<TrainingExerciseDao>(relaxed = true)

    private val repository = SessionRepositoryImpl(
        database = database,
        dao = sessionDao,
        performedExerciseDao = performedExerciseDao,
        setDao = setDao,
        trainingDao = trainingDao,
        exerciseDao = exerciseDao,
        trainingExerciseDao = trainingExerciseDao,
        ioDispatcher = dispatcher,
    )

    @BeforeEach
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Any?>(any()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `getSessionDetail returns null for unknown uuid`() = runTest(dispatcher) {
        val sessionUuid = Uuid.random()
        coEvery { sessionDao.getById(sessionUuid) } returns null

        val result = repository.getSessionDetail(sessionUuid.toString())

        assertNull(result)
    }

    @Test
    fun `getSessionDetail assembles hierarchical detail for a known finished session`() = runTest(dispatcher) {
        val fixture = detailFixture()
        stubSuccessfulLookup(fixture)

        val result = repository.getSessionDetail(fixture.session.uuid.toString())

        requireNotNull(result)
        assertEquals(fixture.session.uuid.toString(), result.sessionUuid)
        assertEquals(fixture.training.uuid.toString(), result.trainingUuid)
        assertEquals("Push Day", result.trainingName)
        assertEquals(false, result.isAdhoc)
        assertEquals(listOf("Bench", "Pull Up"), result.exercises.map { it.exerciseName })
        assertEquals(
            listOf(ExerciseTypeDataModel.WEIGHTED, ExerciseTypeDataModel.WEIGHTLESS),
            result.exercises.map { it.exerciseType },
        )
        assertEquals(listOf(0, 1), result.exercises.map { it.position })
        assertEquals(listOf(5, 8), result.exercises.first().sets.map { it.reps })
        assertEquals(listOf(100.0, 105.0), result.exercises.first().sets.map { it.weight })
    }

    @Test
    fun `getSessionDetail queries every DAO inside the transaction block`() = runTest(dispatcher) {
        val fixture = detailFixture()
        stubSuccessfulLookup(fixture)

        repository.getSessionDetail(fixture.session.uuid.toString())

        coVerify(exactly = 1) { database.withTransaction<Any?>(any()) }
        coVerify(exactly = 1) { sessionDao.getById(fixture.session.uuid) }
        coVerify(exactly = 1) { trainingDao.getById(fixture.training.uuid) }
        coVerify(exactly = 1) { performedExerciseDao.getBySession(fixture.session.uuid) }
        coVerify(exactly = 1) {
            exerciseDao.getByUuids(listOf(fixture.firstExercise.uuid, fixture.secondExercise.uuid))
        }
        coVerify(exactly = 1) { setDao.getByPerformedExercise(fixture.firstPerformed.uuid) }
        coVerify(exactly = 1) { setDao.getByPerformedExercise(fixture.secondPerformed.uuid) }
    }

    private fun stubSuccessfulLookup(fixture: DetailFixture) {
        coEvery { sessionDao.getById(fixture.session.uuid) } returns fixture.session
        coEvery { trainingDao.getById(fixture.training.uuid) } returns fixture.training
        coEvery { performedExerciseDao.getBySession(fixture.session.uuid) } returns listOf(
            fixture.secondPerformed,
            fixture.firstPerformed,
        )
        coEvery {
            exerciseDao.getByUuids(listOf(fixture.firstExercise.uuid, fixture.secondExercise.uuid))
        } returns listOf(fixture.firstExercise, fixture.secondExercise)
        coEvery { setDao.getByPerformedExercise(fixture.firstPerformed.uuid) } returns listOf(
            fixture.firstSetSecond,
            fixture.firstSetFirst,
        )
        coEvery { setDao.getByPerformedExercise(fixture.secondPerformed.uuid) } returns listOf(
            fixture.secondSet,
        )
    }

    private fun detailFixture(): DetailFixture {
        val training = TrainingEntity(
            uuid = Uuid.random(),
            name = "Push Day",
            description = null,
            isAdhoc = false,
            archived = false,
            createdAt = 0L,
            archivedAt = null,
        )
        val session = SessionEntity(
            uuid = Uuid.random(),
            trainingUuid = training.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 1_000L,
            finishedAt = 4_000L,
        )
        val firstExercise = ExerciseEntity(
            uuid = Uuid.random(),
            name = "Bench",
            type = ExerciseTypeEntity.WEIGHTED,
            description = null,
            imagePath = null,
            archived = false,
            createdAt = 0L,
            archivedAt = null,
            lastAdhocSets = null,
        )
        val secondExercise = ExerciseEntity(
            uuid = Uuid.random(),
            name = "Pull Up",
            type = ExerciseTypeEntity.WEIGHTLESS,
            description = null,
            imagePath = null,
            archived = false,
            createdAt = 0L,
            archivedAt = null,
            lastAdhocSets = null,
        )
        val firstPerformed = PerformedExerciseEntity(
            uuid = Uuid.random(),
            sessionUuid = session.uuid,
            exerciseUuid = firstExercise.uuid,
            position = 0,
            skipped = false,
        )
        val secondPerformed = PerformedExerciseEntity(
            uuid = Uuid.random(),
            sessionUuid = session.uuid,
            exerciseUuid = secondExercise.uuid,
            position = 1,
            skipped = false,
        )
        return DetailFixture(
            training = training,
            session = session,
            firstExercise = firstExercise,
            secondExercise = secondExercise,
            firstPerformed = firstPerformed,
            secondPerformed = secondPerformed,
            firstSetFirst = SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = firstPerformed.uuid,
                position = 0,
                reps = 5,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
            firstSetSecond = SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = firstPerformed.uuid,
                position = 1,
                reps = 8,
                weight = 105.0,
                type = SetTypeEntity.FAIL,
            ),
            secondSet = SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = secondPerformed.uuid,
                position = 0,
                reps = 12,
                weight = null,
                type = SetTypeEntity.WORK,
            ),
        )
    }

    private data class DetailFixture(
        val training: TrainingEntity,
        val session: SessionEntity,
        val firstExercise: ExerciseEntity,
        val secondExercise: ExerciseEntity,
        val firstPerformed: PerformedExerciseEntity,
        val secondPerformed: PerformedExerciseEntity,
        val firstSetFirst: SetEntity,
        val firstSetSecond: SetEntity,
        val secondSet: SetEntity,
    )
}
