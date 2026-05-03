// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.training

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
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

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class TrainingDaoRecentTemplatesTest : BaseDatabaseTest() {

    private val trainingDao
        get() = database.trainingDao
    private val trainingExerciseDao
        get() = database.trainingExerciseDao
    private val exerciseDao
        get() = database.exerciseDao
    private val sessionDao
        get() = database.sessionDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `observeRecentTemplates orders used trainings first and filters archived plus adhoc`() = runTest {
        val pushUuid = Uuid.random()
        val pullUuid = Uuid.random()
        val alphaUuid = Uuid.random()
        val zuluUuid = Uuid.random()
        val archivedUuid = Uuid.random()
        val adhocUuid = Uuid.random()
        seedTraining(pushUuid, "Push Day", isAdhoc = false, archived = false)
        seedTraining(pullUuid, "Pull Day", isAdhoc = false, archived = false)
        seedTraining(alphaUuid, "Alpha Day", isAdhoc = false, archived = false)
        seedTraining(zuluUuid, "Zulu Day", isAdhoc = false, archived = false)
        seedTraining(archivedUuid, "Archived Day", isAdhoc = false, archived = true)
        seedTraining(adhocUuid, "Adhoc Day", isAdhoc = true, archived = false)

        seedExercise(Uuid.random(), "Bench")
        seedExercise(Uuid.random(), "Row")
        seedExercise(Uuid.random(), "Squat")
        val exerciseIds = database.exerciseDao.getAllActive().map { it.uuid }
        linkExercises(pushUuid, exerciseIds.take(2))
        linkExercises(pullUuid, exerciseIds.take(1))
        linkExercises(alphaUuid, exerciseIds)
        linkExercises(zuluUuid, exerciseIds.take(1))
        linkExercises(archivedUuid, exerciseIds.take(1))
        linkExercises(adhocUuid, exerciseIds.take(1))

        insertFinishedSession(pushUuid, finishedAt = 3_000L)
        insertFinishedSession(pullUuid, finishedAt = 2_000L)
        insertFinishedSession(archivedUuid, finishedAt = 4_000L)
        insertFinishedSession(adhocUuid, finishedAt = 5_000L)

        val rows = trainingDao.observeRecentTemplates(limit = 10).first()

        assertEquals(
            listOf("Push Day", "Pull Day", "Alpha Day", "Zulu Day"),
            rows.map { it.name },
        )
        assertEquals(2, rows[0].exerciseCount)
        assertEquals(3_000L, rows[0].lastSessionAt)
        assertEquals(1, rows[1].exerciseCount)
        assertEquals(2_000L, rows[1].lastSessionAt)
        assertEquals(3, rows[2].exerciseCount)
        assertNull(rows[2].lastSessionAt)
        assertEquals(1, rows[3].exerciseCount)
        assertNull(rows[3].lastSessionAt)
    }

    @Test
    fun `observeRecentTemplates respects limit`() = runTest {
        val firstUuid = Uuid.random()
        val secondUuid = Uuid.random()
        val thirdUuid = Uuid.random()
        seedTraining(firstUuid, "First", isAdhoc = false, archived = false)
        seedTraining(secondUuid, "Second", isAdhoc = false, archived = false)
        seedTraining(thirdUuid, "Third", isAdhoc = false, archived = false)

        insertFinishedSession(firstUuid, finishedAt = 3_000L)
        insertFinishedSession(secondUuid, finishedAt = 2_000L)
        insertFinishedSession(thirdUuid, finishedAt = 1_000L)

        val rows = trainingDao.observeRecentTemplates(limit = 2).first()

        assertEquals(listOf("First", "Second"), rows.map { it.name })
    }

    private suspend fun seedTraining(
        uuid: Uuid,
        name: String,
        isAdhoc: Boolean,
        archived: Boolean,
    ) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = name,
                description = null,
                isAdhoc = isAdhoc,
                archived = archived,
                createdAt = 0L,
                archivedAt = archived.takeIf { it }?.let { 100L },
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

    private suspend fun linkExercises(trainingUuid: Uuid, exerciseUuids: List<Uuid>) {
        trainingExerciseDao.insert(
            exerciseUuids.mapIndexed { index, exerciseUuid ->
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = index,
                )
            },
        )
    }

    private suspend fun insertFinishedSession(trainingUuid: Uuid, finishedAt: Long) {
        sessionDao.insert(
            SessionEntity(
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
    }
}
