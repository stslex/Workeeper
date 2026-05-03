// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.training

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class TrainingDaoPagedActiveWithStatsTest : BaseDatabaseTest() {

    private val trainingDao get() = database.trainingDao
    private val exerciseDao get() = database.exerciseDao
    private val trainingExerciseDao get() = database.trainingExerciseDao
    private val sessionDao get() = database.sessionDao
    private val tagDao get() = database.tagDao
    private val trainingTagDao get() = database.trainingTagDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `pagedActiveWithStats returns trainings with exercise count and stats columns`() = runTest {
        val pushUuid = Uuid.random()
        val pullUuid = Uuid.random()
        val emptyUuid = Uuid.random()
        seedTraining(pushUuid, "Push Day", isAdhoc = false, archived = false)
        seedTraining(pullUuid, "Pull Day", isAdhoc = false, archived = false)
        seedTraining(emptyUuid, "Empty Day", isAdhoc = false, archived = false)
        // archived/adhoc must NOT appear in the projection.
        val archivedUuid = Uuid.random()
        val adhocUuid = Uuid.random()
        seedTraining(archivedUuid, "Archived", isAdhoc = false, archived = true)
        seedTraining(adhocUuid, "Adhoc", isAdhoc = true, archived = false)

        seedExercise("Bench")
        seedExercise("Squat")
        val exerciseUuids = exerciseDao.getAllActive().map { it.uuid }
        linkExercises(pushUuid, exerciseUuids)
        linkExercises(pullUuid, exerciseUuids.take(1))

        // Active session points at Push Day; finished session at Pull Day's history.
        val activeSession = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = activeSession,
                trainingUuid = pushUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 5_000L,
                finishedAt = null,
            ),
        )
        sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = pullUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 1_000L,
                finishedAt = 2_500L,
            ),
        )

        val rows = loadAllRows()

        // Expect alpha order over trainings (Empty Day, Pull Day, Push Day).
        assertEquals(
            listOf("Empty Day", "Pull Day", "Push Day"),
            rows.map { it.name },
        )
        val push = rows.single { it.name == "Push Day" }
        assertEquals(2, push.exerciseCount)
        assertEquals(activeSession, push.activeSessionUuid)
        assertEquals(5_000L, push.activeSessionStartedAt)
        assertNull(push.lastSessionAt)
        val pull = rows.single { it.name == "Pull Day" }
        assertEquals(1, pull.exerciseCount)
        assertEquals(2_500L, pull.lastSessionAt)
        assertNull(pull.activeSessionUuid)
        val empty = rows.single { it.name == "Empty Day" }
        assertEquals(0, empty.exerciseCount)
        assertNull(empty.activeSessionUuid)
        assertNull(empty.lastSessionAt)
    }

    @Test
    fun `pagedActiveWithStatsByTags filters by tag membership`() = runTest {
        val pushUuid = Uuid.random()
        val pullUuid = Uuid.random()
        seedTraining(pushUuid, "Push Day", isAdhoc = false, archived = false)
        seedTraining(pullUuid, "Pull Day", isAdhoc = false, archived = false)
        val tagUuid = Uuid.random()
        tagDao.insert(TagEntity(uuid = tagUuid, name = "Upper"))
        trainingTagDao.insert(
            listOf(
                TrainingTagEntity(trainingUuid = pushUuid, tagUuid = tagUuid),
            ),
        )

        val pagingSource = trainingDao.pagedActiveWithStatsByTags(listOf(tagUuid))
        val page = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("Push Day"), page.data.map { it.name })
    }

    @Test
    fun `pagedActiveWithStatsByTags returns empty when no matches`() = runTest {
        seedTraining(Uuid.random(), "Push Day", isAdhoc = false, archived = false)
        val tagUuid = Uuid.random()
        tagDao.insert(TagEntity(uuid = tagUuid, name = "Upper"))

        val pagingSource = trainingDao.pagedActiveWithStatsByTags(listOf(tagUuid))
        val page = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertNotNull(page)
        assertEquals(emptyList<String>(), page.data.map { it.name })
    }

    private suspend fun loadAllRows() = (
        trainingDao.pagedActiveWithStats().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        ).data

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
                archivedAt = if (archived) 100L else null,
            ),
        )
    }

    private suspend fun seedExercise(name: String) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = Uuid.random(),
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
                    planSets = null,
                )
            },
        )
    }
}
