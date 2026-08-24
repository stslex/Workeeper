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
 * Home's recent-session list against a real database.
 *
 * Replaces `SessionDaoRecentWithStatsTest`, whose two cases were "returns rows with counts" and
 * "respects limit" — and the limit is gone. What takes its place is not one more case: **a limit of
 * ten hides what a predicate does at scale**, so the query's filters are now asserted individually,
 * including the one that excludes rows silently and the one whose value turns out to be dead.
 *
 * Every case here writes rows and reads them back through Room. None of it is a claim from reading
 * SQL — §0.3 records this arc's behavioural-reading claims failing seven for seven, and two of the
 * findings below contradicted a first reading of the same query.
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
        // Two sets on the one non-skipped exercise: `exercise_count` filters `skipped = 0`,
        // `set_count` does not filter at all, so the two subqueries must disagree here or neither
        // is being exercised.
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
        // The in-progress row is given a NON-NULL `finished_at`, which is unnatural for the app and
        // is the whole point: with the natural `null` the row is excluded by the *second* predicate
        // too, and the two filters overlap so completely that this case cannot see the first one.
        //
        // Measured, not reasoned: the first draft seeded `finishedAt = null` here and deleting
        // `s.state = 'FINISHED'` from the query left this case GREEN. A test that two predicates
        // both satisfy tells you nothing about either.
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
        // `ORDER BY finished_at DESC` on a nullable column parks nulls LAST on SQLite, so without
        // the `IS NOT NULL` predicate such a row would not vanish — it would sit permanently below
        // every dated session, which is a different and worse bug than being absent. The predicate
        // is asserted here rather than assumed to be belt-and-braces with the state filter.
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
        // **This case is here because it started out asserting the opposite, and a mutation caught
        // it.** It was written as "the INNER JOIN silently drops a session whose training row is
        // gone" — a filter nobody chose, invisible behind a limit of ten, the shape of a B24
        // absence. Then `INNER JOIN` → `LEFT JOIN` was run as a controlled mutation and the case
        // stayed **green**, which it could not have done if an orphan ever reached the query.
        //
        // The reason is in the schema, not in the query: `SessionEntity`'s foreign key on
        // `training_uuid` is `onDelete = ForeignKey.CASCADE`, so deleting a training deletes its
        // sessions in the same statement. There is no orphan for the join to drop, and there
        // cannot be one while that key stands — which is a far stronger guarantee than "no current
        // delete path produces one", and it is what the row now records.
        //
        // The generalisable half is B23's, reproduced while writing a test about reachability:
        // **a test that builds its own precondition cannot tell you whether the precondition is
        // reachable.** The first version hand-built a state the database forbids and would have
        // passed forever, certifying a filter that never fires.
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
        // Two findings in one case, and the second is the one worth having.
        //
        // (1) There is no `is_adhoc` predicate: an ad-hoc training's finished session IS returned.
        //     Home is the only surface in the app that shows them at all — every trainings list
        //     filters `is_adhoc = 0`.
        //
        // (2) And yet `RecentSessionItem.isAdhoc` is dead for anything the app produces, because
        //     `finishSessionAtomic` calls `trainingDao.graduateTraining(...)` unconditionally in
        //     the same transaction as the FINISHED flip. This case reproduces both halves: the row
        //     written directly with `is_adhoc = 1` comes back `true` (so the column really is
        //     passed through and the flag is not being dropped somewhere), and the same training
        //     after graduation comes back `false`.
        //
        //     The reachable exception is a restore of legacy or hand-edited backup data, which is
        //     why the column stays selected instead of being deleted from the projection.
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
        // The change this file exists for. The predecessor asserted `limit = 2` returned 2 rows;
        // the honest assertion now is that row eleven is REACHABLE, since being unreachable from
        // Home is exactly what the hardcoded ten did.
        val training = Uuid.random()
        seedTraining(training, "Push Day", isAdhoc = false)
        repeat(TOTAL_SESSIONS) { index ->
            insertFinishedSession(training, finishedAt = (index + 1) * 1_000L) { emptyList() }
        }

        val first = sessionDao.pagedRecentWithStats().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = PAGE, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals(PAGE, first.data.size)

        // Append until the source says there is nothing after it. Walking to exhaustion rather
        // than asserting one append's size is the point: the claim is that row eleven is
        // REACHABLE, and one page of five cannot show that. (`loadSize` bounds each append, so a
        // second page returns PAGE items, not "everything left" — the first draft of this case
        // asserted the latter and was wrong by 4.)
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
