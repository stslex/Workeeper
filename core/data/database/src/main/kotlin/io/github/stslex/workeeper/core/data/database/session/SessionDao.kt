package io.github.stslex.workeeper.core.data.database.session

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * The one PR row shape. `exercise_table` is joined in so the *exercise* type is read from
 * the DB rather than passed in by a caller that read it separately — a caller-supplied type
 * can go stale between the read and the query and silently reorder the result.
 *
 * `s.type` is the **set** type (WARM/WORK/FAIL/DROP); `e.type` is the **exercise** type
 * (WEIGHTED/WEIGHTLESS). Only `s.type` is projected.
 */
private const val PR_ROW_SELECT = """
        SELECT pe.exercise_uuid AS exercise_uuid,
               s.uuid AS set_uuid,
               s.weight AS weight,
               s.reps AS reps,
               s.type AS type,
               s.performed_exercise_uuid AS performed_exercise_uuid,
               sn.uuid AS session_uuid,
               sn.finished_at AS finished_at
        FROM set_table s
        JOIN performed_exercise_table pe ON pe.uuid = s.performed_exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        JOIN exercise_table e ON e.uuid = pe.exercise_uuid
        """

/**
 * Which sets may hold the record. A set is eligible iff its session is FINISHED with a
 * non-null `finished_at`, it logged at least one rep, and — for WEIGHTED exercises only —
 * it carries a weight. WEIGHTLESS exercises ignore `set_table.weight` entirely: residual
 * non-null weights on weightless rows exist in the wild and must not gate eligibility.
 *
 * Appended after the caller's own `WHERE pe.exercise_uuid …` predicate, hence the leading
 * `AND`.
 */
private const val PR_ELIGIBILITY = """
          AND sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
          AND s.reps > 0
          AND (e.type = 'WEIGHTLESS' OR s.weight IS NOT NULL)
        """

/**
 * Who wins among eligible sets: weight DESC (WEIGHTED only), then reps DESC, then earliest
 * `finished_at`, then lowest `position`. The `CASE` collapses to a constant NULL for
 * WEIGHTLESS exercises, so weight drops out of the comparison instead of being coerced.
 * Weight is never NULL for a WEIGHTED exercise here — [PR_ELIGIBILITY] already excluded
 * those rows — so SQLite's NULL-ordering rules never come into play.
 */
private const val PR_ORDER = """
            CASE WHEN e.type = 'WEIGHTED' THEN s.weight END DESC,
            s.reps DESC,
            sn.finished_at ASC,
            s.position ASC
        """

/** Single-exercise PR: the one holder row, or nothing. */
private const val PR_SINGLE_SQL = """
        $PR_ROW_SELECT
        WHERE pe.exercise_uuid = :exerciseUuid
        $PR_ELIGIBILITY
        ORDER BY
        $PR_ORDER
        LIMIT 1
        """

/**
 * Batch PR: *every* eligible candidate for every requested exercise, grouped by exercise and
 * ordered so the consumer takes `.first()` per group. No `LIMIT`/window function — `minSdk 28`
 * ships SQLite 3.22 and `ROW_NUMBER()` needs 3.25 (the bundled-SQLite dependency is declared
 * but inert; `AndroidSQLiteDriver` uses framework SQLite).
 */
private const val PR_BATCH_SQL = """
        $PR_ROW_SELECT
        WHERE pe.exercise_uuid IN (:exerciseUuids)
        $PR_ELIGIBILITY
        ORDER BY pe.exercise_uuid,
        $PR_ORDER
        """

@Suppress("TooManyFunctions")
@Dao
interface SessionDao {

    @Query("SELECT * FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query(
        """
        SELECT uuid, training_uuid, started_at FROM session_table
        WHERE state = 'IN_PROGRESS' LIMIT 1
        """,
    )
    fun observeAnyActiveSession(): Flow<ActiveSessionRow?>

    @Query(
        """
        SELECT s.uuid AS uuid,
               s.training_uuid AS training_uuid,
               t.name AS training_name,
               t.is_adhoc AS is_adhoc,
               s.started_at AS started_at,
               (SELECT COUNT(*) FROM performed_exercise_table pe
                 WHERE pe.session_uuid = s.uuid AND pe.skipped = 0) AS total_count,
               (SELECT COUNT(DISTINCT pe.uuid) FROM performed_exercise_table pe
                 INNER JOIN set_table st ON st.performed_exercise_uuid = pe.uuid
                 WHERE pe.session_uuid = s.uuid AND pe.skipped = 0) AS done_count
        FROM session_table s
        INNER JOIN training_table t ON t.uuid = s.training_uuid
        WHERE s.state = 'IN_PROGRESS'
        LIMIT 1
        """,
    )
    fun observeActiveSessionWithStats(): Flow<ActiveSessionWithStatsRow?>

    @Query("SELECT * FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1")
    suspend fun getActive(): SessionEntity?

    /**
     * The Home recent-sessions list, **paged**.
     *
     * Replaces `observeRecentWithStats(limit)`, which Home called at a hardcoded 10, and the
     * unrelated `observeRecent(limit)` beside it, which had no production consumer at all. The
     * projection is unchanged; the only edit is that `LIMIT :limit` is gone.
     *
     * ## The filters, stated because a limit of ten hid what they do at scale
     *
     * Two predicates and one thing that looks like a third:
     *
     * 1. `s.state = 'FINISHED'` — in-progress sessions are Home's banner, not its list.
     * 2. `s.finished_at IS NOT NULL` — **not** belt and braces with (1). It is load-bearing for the
     *    sort: `ORDER BY … DESC` on a nullable column parks nulls at the *tail* on SQLite, so a
     *    FINISHED row with no timestamp would not vanish — it would sit below every dated session
     *    forever, which is a worse failure than being absent.
     * 3. **`INNER JOIN training_table` excludes nothing, and the first draft of this KDoc said it
     *    did.** It was recorded here as the silent filter — a session whose training row is gone
     *    gets dropped, nobody chose it, invisible behind a limit of ten. Then `INNER JOIN` →
     *    `LEFT JOIN` was run as a controlled mutation against the test suite and **every case
     *    stayed green**, which is impossible if an orphan can ever reach this query.
     *
     *    The reason is in the schema: `SessionEntity`'s foreign key on `training_uuid` carries
     *    `onDelete = ForeignKey.CASCADE`, so deleting a training deletes its sessions in the same
     *    statement. There is no orphan for the join to drop and there cannot be one while that key
     *    stands — a stronger guarantee than "no current delete path makes one", and the opposite
     *    conclusion to the B24-shaped warning this row used to carry. The join is here to read
     *    `t.name`, and that is all it does.
     *
     * ## What it does NOT filter, and what that turned out to mean
     *
     * There is **no `is_adhoc` predicate** — the query *selects* `t.is_adhoc` and passes it
     * through, which reads as "Home deliberately shows ad-hoc sessions". Measured, the flag is
     * dead by construction: `finishSessionAtomic` calls `trainingDao.graduateTraining(…)`
     * unconditionally, in the same transaction as the `FINISHED` flip, so **every** row this query
     * can return has `is_adhoc = 0`. See `SessionDaoPagedRecentWithStatsTest`, which asserts it
     * against a real database rather than leaving it a reading. **Filed as B29** — a shipped
     * treatment nothing can reach, same family as B23 and the occluded settings gear, and what is
     * owed there is a decision rather than a fix.
     *
     * The one reachable exception is a **restore**: a backup written before graduation existed, or
     * hand-edited, can insert a FINISHED session under an `is_adhoc = 1` training, and this query
     * will return it with the flag set. That is why the column stays selected rather than being
     * dropped — the value is honest, it is simply almost always false.
     */
    @Query(
        """
        SELECT s.uuid AS session_uuid,
               s.training_uuid AS training_uuid,
               t.name AS training_name,
               t.is_adhoc AS is_adhoc,
               s.started_at AS started_at,
               s.finished_at AS finished_at,
               (SELECT COUNT(*) FROM performed_exercise_table pe
                  WHERE pe.session_uuid = s.uuid AND pe.skipped = 0) AS exercise_count,
               (SELECT COUNT(*) FROM set_table st
                  JOIN performed_exercise_table pe2 ON pe2.uuid = st.performed_exercise_uuid
                  WHERE pe2.session_uuid = s.uuid) AS set_count
        FROM session_table s
        INNER JOIN training_table t ON t.uuid = s.training_uuid
        WHERE s.state = 'FINISHED' AND s.finished_at IS NOT NULL
        ORDER BY s.finished_at DESC
        """,
    )
    fun pagedRecentWithStats(): PagingSource<Int, RecentSessionRow>

    @Query(
        """
        SELECT * FROM session_table
        WHERE state = 'FINISHED'
        ORDER BY finished_at DESC
        """,
    )
    fun pagedFinished(): PagingSource<Int, SessionEntity>

    @Query(
        """
        SELECT * FROM session_table
        WHERE training_uuid = :trainingUuid AND state = 'FINISHED'
        ORDER BY finished_at DESC
        """,
    )
    fun pagedFinishedByTraining(trainingUuid: Uuid): PagingSource<Int, SessionEntity>

    @Query(
        """
        SELECT * FROM session_table
        WHERE training_uuid = :trainingUuid AND state = 'FINISHED'
        ORDER BY finished_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentFinishedByTraining(
        trainingUuid: Uuid,
        limit: Int,
    ): List<SessionEntity>

    @Query("SELECT * FROM session_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): SessionEntity?

    /**
     * Every session row, unfiltered — includes both `IN_PROGRESS` and `FINISHED`.
     * Bypasses the `state`-filtered list invariant: the only caller is the
     * AI-readable snapshot export, which dumps the full history. Not for user-facing
     * reads.
     */
    @Query("SELECT * FROM session_table")
    suspend fun getAll(): List<SessionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM session_table
        WHERE training_uuid = :trainingUuid AND state = 'FINISHED'
        """,
    )
    suspend fun countFinishedByTraining(trainingUuid: Uuid): Int

    @Query(
        """
        SELECT COUNT(DISTINCT pe.session_uuid) FROM performed_exercise_table pe
        JOIN session_table s ON s.uuid = pe.session_uuid
        WHERE pe.exercise_uuid = :exerciseUuid AND s.state = 'FINISHED'
        """,
    )
    suspend fun countFinishedContainingExercise(exerciseUuid: Uuid): Int

    @Query(
        """
        SELECT s.uuid AS session_uuid,
               pe.uuid AS performed_exercise_uuid,
               s.finished_at AS finished_at,
               t.name AS training_name,
               t.is_adhoc AS is_adhoc
        FROM session_table s
        JOIN training_table t ON t.uuid = s.training_uuid
        JOIN performed_exercise_table pe ON pe.session_uuid = s.uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND s.state = 'FINISHED'
          AND s.finished_at IS NOT NULL
          AND EXISTS (
            SELECT 1 FROM set_table st
            WHERE st.performed_exercise_uuid = pe.uuid
          )
        GROUP BY s.uuid
        ORDER BY s.finished_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentSessionsForExercise(
        exerciseUuid: Uuid,
        limit: Int,
    ): List<SessionHistoryRow>

    @Insert
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM session_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Insert
    suspend fun insertPerformedExercises(rows: List<PerformedExerciseEntity>)

    /**
     * Atomically creates a session + its performed_exercise rows. Lets the LiveWorkout
     * domain start a session in a single transaction without dragging room-ktx into
     * core/exercise.
     */
    @Transaction
    suspend fun startSessionWithExercises(
        session: SessionEntity,
        performedExercises: List<PerformedExerciseEntity>,
    ) {
        insert(session)
        if (performedExercises.isNotEmpty()) {
            insertPerformedExercises(performedExercises)
        }
    }

    /**
     * Atomically marks a session FINISHED at [finishedAt]. Returns the finished entity (or
     * null if the session was already gone). Wrapper kept here so the impl can chain plan
     * updates inside the same transaction in the future without re-routing through repos.
     */
    @Transaction
    suspend fun finishSession(uuid: Uuid, finishedAt: Long) {
        val current = getById(uuid) ?: return
        update(
            current.copy(
                state = SessionStateEntity.FINISHED,
                finishedAt = finishedAt,
            ),
        )
    }

    /**
     * The set that holds the record for [exerciseUuid], or null when no finished session has
     * logged an eligible set yet. Eligibility is [PR_ELIGIBILITY], ordering is [PR_ORDER] —
     * both shared verbatim with [observePersonalRecord] and [observePersonalRecordsBatch], so
     * the three cannot drift. The exercise type is read from `exercise_table`, not passed in.
     */
    @Query(PR_SINGLE_SQL)
    suspend fun getPersonalRecord(exerciseUuid: Uuid): PersonalRecordRow?

    /**
     * Reactive form of [getPersonalRecord] — literally the same SQL body ([PR_SINGLE_SQL]).
     * Room re-emits whenever any participating table changes, so subscribers see the new
     * holder after a finished session bumps it, an edit-save changes a top set, or the
     * holder set is deleted.
     */
    @Query(PR_SINGLE_SQL)
    fun observePersonalRecord(exerciseUuid: Uuid): Flow<PersonalRecordRow?>

    /**
     * Reactive PR rows across many exercises in a single subscription. Returns *all* eligible
     * candidates, contiguous per exercise and best-first within each group, so the consumer
     * picks holders with `groupBy { exerciseUuid }.mapValues { it.first() }`. One subscription
     * instead of the combine-of-N amplification long-lived subscribers (Past session, Live
     * workout pre-snapshot) would otherwise hit.
     *
     * Eligibility and ordering are the same constants [getPersonalRecord] uses. Because the
     * eligibility predicate now lives here, consumers must not re-filter candidates — half a
     * rule in a second module is how the two paths diverged before.
     */
    @Query(PR_BATCH_SQL)
    fun observePersonalRecordsBatch(exerciseUuids: List<Uuid>): Flow<List<PersonalRecordRow>>

    /**
     * Top-N finished sessions by volume in the window starting at [sinceMillis]. Volume is
     * `Σ(weight × reps)` over weighted sets only — weightless exercises and weight-null
     * sets are filtered before the sum so they never inflate the metric.
     */
    @Query(
        """
        SELECT sn.uuid AS session_uuid,
               sn.training_uuid AS training_uuid,
               sn.finished_at AS finished_at,
               SUM(s.weight * s.reps) AS volume
        FROM session_table sn
        JOIN performed_exercise_table pe ON pe.session_uuid = sn.uuid
        JOIN exercise_table e ON e.uuid = pe.exercise_uuid
        JOIN set_table s ON s.performed_exercise_uuid = pe.uuid
        WHERE sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
          AND sn.finished_at >= :sinceMillis
          AND e.type = 'WEIGHTED'
          AND s.weight IS NOT NULL
        GROUP BY sn.uuid
        HAVING volume IS NOT NULL
        ORDER BY volume DESC, sn.finished_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getBestSessionVolumes(sinceMillis: Long, limit: Int): List<BestSessionVolumeRow>

    /**
     * Date-ordered set list for [exerciseUuid] across finished sessions. Each row carries
     * its parent session metadata so the consumer (Exercise detail history, v2.2 charts)
     * can group rows into sessions without an extra round trip per row.
     */
    @Query(
        """
        SELECT sn.uuid AS session_uuid,
               sn.finished_at AS finished_at,
               sn.training_uuid AS training_uuid,
               t.name AS training_name,
               t.is_adhoc AS is_adhoc,
               s.weight AS weight,
               s.reps AS reps,
               s.position AS position,
               s.type AS set_type
        FROM set_table s
        JOIN performed_exercise_table pe ON pe.uuid = s.performed_exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        JOIN training_table t ON t.uuid = sn.training_uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
        ORDER BY sn.finished_at DESC, s.position ASC
        """,
    )
    fun pagedHistoryByExercise(exerciseUuid: Uuid): PagingSource<Int, HistoryByExerciseRow>

    @Query(
        """
        SELECT sn.uuid AS session_uuid,
               sn.finished_at AS finished_at,
               sn.training_uuid AS training_uuid,
               t.name AS training_name,
               t.is_adhoc AS is_adhoc,
               s.weight AS weight,
               s.reps AS reps,
               s.position AS position,
               s.type AS set_type
        FROM set_table s
        JOIN performed_exercise_table pe ON pe.uuid = s.performed_exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        JOIN training_table t ON t.uuid = sn.training_uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
        ORDER BY sn.finished_at DESC, s.position ASC
        """,
    )
    suspend fun getHistoryByExercise(exerciseUuid: Uuid): List<HistoryByExerciseRow>
}
