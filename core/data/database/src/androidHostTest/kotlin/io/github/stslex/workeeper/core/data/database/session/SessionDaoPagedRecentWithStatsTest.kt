// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

/**
 * Home's recent-session list against a real database. Each filter of the query is asserted on its
 * own — a limit of ten used to hide what a predicate does at scale.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SessionDaoPagedRecentWithStatsTest : BaseDatabaseTest() {

    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val setDao get() = database.setDao
    private val trainingDao get() = database.trainingDao
    private val exerciseDao get() = database.exerciseDao

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    @DisplayName("newest first, with per-row exercise and set counts")
    fun ordersNewestFirstWithCounts() = runTest {
        val template = Uuid.random()
        val bench = Uuid.random()
        val fly = Uuid.random()
        seedTraining(template, "Push Day", isAdhoc = false)
        seedExercise(bench, "Bench")
        seedExercise(fly, "Fly")

        val newest = insertFinishedSession(template, finishedAt = 3_000L) {
            listOf(performed(bench, 0, skipped = false), performed(fly, 1, skipped = true))
        }
        // Two sets on the one non-skipped exercise, so the two count subqueries must disagree.
        val performedRows = performedExerciseDao.getBySession(newest)
            .filter { it.exerciseUuid == bench }
        repeat(2) { index ->
            setDao.insert(
                SetEntity(
                    performedExerciseUuid = performedRows.first().uuid,
                    position = index,
                    reps = 5,
                    weight = 100.0,
                    type = SetTypeEntity.WORK,
                ),
            )
        }
        val middle = insertFinishedSession(template, finishedAt = 2_000L) {
            listOf(performed(bench, 0, skipped = false))
        }
        val oldest = insertFinishedSession(template, finishedAt = 1_000L) {
            listOf(performed(fly, 0, skipped = true))
        }

        val rows = loadAll()

        assertEquals(listOf(newest, middle, oldest), rows.map { it.sessionUuid })
        assertEquals(1, rows[0].exerciseCount)
        assertEquals(2, rows[0].setCount)
        assertEquals(1, rows[1].exerciseCount)
        assertEquals(0, rows[1].setCount)
        // A session of nothing but skipped exercises: zero, not one.
        assertEquals(0, rows[2].exerciseCount)
    }

    @Test
    @DisplayName("filter 1: only FINISHED sessions — in-progress is Home's banner, not its list")
    fun excludesInProgress() = runTest {
        val training = Uuid.random()
        seedTraining(training, "Push Day", isAdhoc = false)
        val finished = insertFinishedSession(training, finishedAt = 1_000L) { emptyList() }
        // The in-progress row gets a NON-NULL `finished_at` on purpose: with the natural `null`
        // the second predicate excludes it too and the case cannot see the state filter at all.
        sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = training,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 5_000L,
                finishedAt = 9_000L,
            ),
        )

        assertEquals(listOf(finished), loadAll().map { it.sessionUuid })
    }

    @Test
    @DisplayName("filter 2: FINISHED with a null finished_at is excluded, not sorted to the tail")
    fun excludesFinishedWithoutTimestamp() = runTest {
        // GUARD: `ORDER BY finished_at DESC` parks nulls LAST on SQLite, so without `IS NOT NULL`
        // such a row would sit permanently below every dated session rather than vanish.
        val training = Uuid.random()
        seedTraining(training, "Push Day", isAdhoc = false)
        val dated = insertFinishedSession(training, finishedAt = 1_000L) { emptyList() }
        sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = training,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = null,
            ),
        )

        assertEquals(listOf(dated), loadAll().map { it.sessionUuid })
    }

    @Test
    @DisplayName("filter 3: the INNER JOIN cannot drop anything — a foreign key forbids the orphan")
    fun deletingATrainingCascadesToItsSessions() = runTest {
        // The INNER JOIN drops nothing: `SessionEntity`'s FK on `training_uuid` is CASCADE, so
        // deleting a training deletes its sessions and no orphan can ever reach the query.
        val liveTraining = Uuid.random()
        seedTraining(liveTraining, "Push Day", isAdhoc = false)
        val kept = insertFinishedSession(liveTraining, finishedAt = 2_000L) { emptyList() }

        val doomedTraining = Uuid.random()
        seedTraining(doomedTraining, "Deleted Later", isAdhoc = false)
        val doomedSession = insertFinishedSession(doomedTraining, finishedAt = 3_000L) { emptyList() }
        // Newest of the two, so it heads the list while it exists — the before-picture matters.
        assertEquals(listOf(doomedSession, kept), loadAll().map { it.sessionUuid })

        trainingDao.permanentDelete(doomedTraining)

        assertEquals(listOf(kept), loadAll().map { it.sessionUuid })
        assertNull(sessionDao.getById(doomedSession)) {
            "the session must be GONE, not merely hidden by the join — CASCADE, not a filter"
        }
    }

    @Test
    @DisplayName("no is_adhoc filter — and the flag it selects is dead for every row the app writes")
    fun adhocFlagIsPassedThroughAndIsFalseInPractice() = runTest {
        // No `is_adhoc` predicate — an ad-hoc training's finished session is returned — yet the
        // flag is false for every row the app writes, because finishing graduates the training.
        val adhoc = Uuid.random()
        seedTraining(adhoc, "Quick Session", isAdhoc = true)
        insertFinishedSession(adhoc, finishedAt = 1_000L) { emptyList() }

        val beforeGraduation = loadAll()
        assertEquals(1, beforeGraduation.size)
        assertTrue(beforeGraduation.single().isAdhoc) {
            "the query has no is_adhoc predicate, so an ad-hoc session must appear"
        }

        trainingDao.graduateTraining(adhoc)

        val afterGraduation = loadAll()
        assertEquals(1, afterGraduation.size)
        assertEquals(false, afterGraduation.single().isAdhoc) {
            "finishSessionAtomic graduates the training in the same transaction as the FINISHED " +
                "flip, so every row the app can write reaches this query with is_adhoc = 0"
        }
    }

    @Test
    @DisplayName("pages past the old ten-row ceiling")
    fun pagesBeyondTheOldLimit() = runTest {
        // The claim is that row eleven is REACHABLE; the hardcoded ten is what made it not.
        val training = Uuid.random()
        seedTraining(training, "Push Day", isAdhoc = false)
        repeat(TOTAL_SESSIONS) { index ->
            insertFinishedSession(training, finishedAt = (index + 1) * 1_000L) { emptyList() }
        }

        val first = sessionDao.pagedRecentWithStats().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = PAGE, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals(PAGE, first.data.size)

        // Walk to exhaustion: one page of five cannot show row eleven is reachable, and `loadSize`
        // bounds each append rather than returning everything left.
        val rows = first.data.toMutableList()
        var key = first.nextKey
        while (key != null) {
            val page = sessionDao.pagedRecentWithStats().load(
                PagingSource.LoadParams.Append(key = key, loadSize = PAGE, placeholdersEnabled = false),
            ) as PagingSource.LoadResult.Page
            rows += page.data
            key = page.nextKey
        }

        assertEquals(TOTAL_SESSIONS, rows.size)
        assertEquals(TOTAL_SESSIONS, rows.map { it.sessionUuid }.toSet().size) {
            "paging must not repeat a row across page boundaries"
        }
        // Still newest-first ACROSS every boundary, which one page cannot show.
        val timestamps = rows.map { it.finishedAt }
        assertEquals(timestamps.sortedDescending(), timestamps)
    }

    private suspend fun loadAll(): List<RecentSessionRow> = (
        sessionDao.pagedRecentWithStats().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        ).data

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
        performedRows: () -> List<PerformedExerciseEntity>,
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
        val rows = performedRows().map { row -> row.copy(sessionUuid = sessionUuid) }
        if (rows.isNotEmpty()) performedExerciseDao.insert(rows)
        return sessionUuid
    }

    private fun performed(exerciseUuid: Uuid, position: Int, skipped: Boolean) =
        PerformedExerciseEntity(
            sessionUuid = Uuid.random(),
            exerciseUuid = exerciseUuid,
            position = position,
            skipped = skipped,
        )

    private companion object {
        /** More than the ten the old query was capped at, and not a multiple of [PAGE]. */
        const val TOTAL_SESSIONS = 14
        const val PAGE = 5
    }
}
