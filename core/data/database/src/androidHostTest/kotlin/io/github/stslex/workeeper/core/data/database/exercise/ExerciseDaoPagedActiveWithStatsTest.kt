// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
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

/**
 * Coverage for the v2.4 E6 paged-with-stats projection. Verifies that the embedded
 * `ExerciseEntity` round-trips and the three correlated subqueries return the same
 * values as the per-exercise `observe*` queries already covered elsewhere.
 */
@Suppress("MagicNumber")
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class ExerciseDaoPagedActiveWithStatsTest : BaseDatabaseTest() {

    private val exerciseDao get() = database.exerciseDao
    private val trainingDao get() = database.trainingDao
    private val trainingExerciseDao get() = database.trainingExerciseDao
    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val tagDao get() = database.tagDao
    private val exerciseTagDao get() = database.exerciseTagDao

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    fun `pagedActiveWithStats returns active library exercises sorted by name`() = runTest {
        val benchUuid = Uuid.random()
        val squatUuid = Uuid.random()
        val archivedUuid = Uuid.random()
        val adhocUuid = Uuid.random()
        seedExercise(benchUuid, "Bench press")
        seedExercise(squatUuid, "Squat")
        seedExercise(archivedUuid, "Archived ex", archived = true)
        seedExercise(adhocUuid, "Adhoc ex", isAdhoc = true)

        val rows = loadAllRows()

        assertEquals(listOf("Bench press", "Squat"), rows.map { it.exercise.name })
    }

    @Test
    fun `sessionCount counts distinct finished non-skipped sessions`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid)
        // Two finished, one skipped, one in-progress.
        seedSessionWithExercise(trainingUuid, exerciseUuid, SessionStateEntity.FINISHED, 1_000L)
        seedSessionWithExercise(trainingUuid, exerciseUuid, SessionStateEntity.FINISHED, 2_000L)
        seedSessionWithExercise(
            trainingUuid,
            exerciseUuid,
            SessionStateEntity.FINISHED,
            3_000L,
            skipped = true,
        )
        seedSessionWithExercise(trainingUuid, exerciseUuid, SessionStateEntity.IN_PROGRESS, null)

        val row = loadAllRows().single { it.exercise.uuid == exerciseUuid }

        assertEquals(2, row.sessionCount)
    }

    @Test
    fun `linkedTrainingsCount counts distinct active library trainings`() = runTest {
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid)
        val activeOne = Uuid.random()
        val activeTwo = Uuid.random()
        val archived = Uuid.random()
        val adhoc = Uuid.random()
        seedTraining(activeOne, archived = false, isAdhoc = false)
        seedTraining(activeTwo, archived = false, isAdhoc = false)
        seedTraining(archived, archived = true, isAdhoc = false)
        seedTraining(adhoc, archived = false, isAdhoc = true)
        listOf(activeOne, activeTwo, archived, adhoc).forEach { trainingUuid ->
            trainingExerciseDao.insert(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    planSets = null,
                ),
            )
        }

        val row = loadAllRows().single { it.exercise.uuid == exerciseUuid }

        assertEquals(2, row.linkedTrainingsCount)
    }

    @Test
    fun `lastTrainedAt is MAX of finished non-skipped session timestamps`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedExercise(exerciseUuid)
        seedSessionWithExercise(trainingUuid, exerciseUuid, SessionStateEntity.FINISHED, 1_000L)
        seedSessionWithExercise(trainingUuid, exerciseUuid, SessionStateEntity.FINISHED, 9_000L)
        seedSessionWithExercise(
            trainingUuid,
            exerciseUuid,
            SessionStateEntity.FINISHED,
            12_000L,
            skipped = true,
        )

        val row = loadAllRows().single { it.exercise.uuid == exerciseUuid }

        assertEquals(9_000L, row.lastTrainedAt)
    }

    @Test
    fun `exercise with zero sessions returns null lastTrainedAt`() = runTest {
        val exerciseUuid = Uuid.random()
        seedExercise(exerciseUuid)

        val row = loadAllRows().single { it.exercise.uuid == exerciseUuid }

        assertEquals(0, row.sessionCount)
        assertEquals(0, row.linkedTrainingsCount)
        assertNull(row.lastTrainedAt)
    }

    @Test
    fun `pagedActiveWithStatsByTags filters by tag with stats preserved`() = runTest {
        val taggedUuid = Uuid.random()
        val untaggedUuid = Uuid.random()
        val tagUuid = Uuid.random()
        seedExercise(taggedUuid, name = "Tagged")
        seedExercise(untaggedUuid, name = "Untagged")
        tagDao.insert(TagEntity(uuid = tagUuid, name = "Upper"))
        exerciseTagDao.insert(
            listOf(ExerciseTagEntity(exerciseUuid = taggedUuid, tagUuid = tagUuid)),
        )
        val trainingUuid = Uuid.random()
        seedTraining(trainingUuid)
        seedSessionWithExercise(trainingUuid, taggedUuid, SessionStateEntity.FINISHED, 4_000L)

        val pagingSource = exerciseDao.pagedActiveWithStatsByTags(listOf(tagUuid))
        val page = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("Tagged"), page.data.map { it.exercise.name })
        assertEquals(1, page.data.single().sessionCount)
        assertEquals(4_000L, page.data.single().lastTrainedAt)
    }

    private suspend fun loadAllRows() = (
        exerciseDao.pagedActiveWithStats().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        ).data

    private suspend fun seedExercise(
        uuid: Uuid,
        name: String = "Exercise-$uuid",
        archived: Boolean = false,
        isAdhoc: Boolean = false,
    ) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = archived,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
                isAdhoc = isAdhoc,
            ),
        )
    }

    private suspend fun seedTraining(
        uuid: Uuid,
        archived: Boolean = false,
        isAdhoc: Boolean = false,
    ) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = "Training-$uuid",
                description = null,
                isAdhoc = isAdhoc,
                archived = archived,
                createdAt = 0L,
                archivedAt = if (archived) 0L else null,
            ),
        )
    }

    private suspend fun seedSessionWithExercise(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        state: SessionStateEntity,
        finishedAt: Long?,
        skipped: Boolean = false,
    ) {
        val sessionUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = state,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = Uuid.random(),
                    sessionUuid = sessionUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = skipped,
                ),
            ),
        )
    }
}
