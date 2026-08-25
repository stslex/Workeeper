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
 * The one PR row shape. `exercise_table` is joined so the exercise type comes from the DB — a
 * caller-supplied type can go stale between the read and the query and silently reorder the result.
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
 * Which sets may hold the record: finished session, at least one rep, and — for WEIGHTED
 * exercises only — a weight. Appended after the caller's own `WHERE`, hence the leading `AND`.
 */
private const val PR_ELIGIBILITY = """
          AND sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
          AND s.reps > 0
          AND (e.type = 'WEIGHTLESS' OR s.weight IS NOT NULL)
        """

/**
 * Who wins among eligible sets: weight DESC (WEIGHTED only), reps DESC, earliest `finished_at`,
 * lowest `position`.
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
 * Batch PR: every eligible candidate for every requested exercise, grouped by exercise and
 * best-first within each group so the consumer takes `.first()` per group.
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
     * The Home recent-sessions list, paged. GUARD: `finished_at IS NOT NULL` is load-bearing for
     * the sort, not redundant with the state filter — SQLite parks NULLs at the tail of DESC.
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

    /**
     * Finish timestamps inside `[startInclusive, endExclusive)` — the Home start card's week
     * readout (home-start-card.md §3.1).
     */
    @Query(
        """
        SELECT finished_at FROM session_table
        WHERE state = 'FINISHED'
          AND finished_at IS NOT NULL
          AND finished_at >= :startInclusive
          AND finished_at < :endExclusive
        """,
    )
    fun observeFinishedTimesBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<Long>>

    /**
     * The most recent finished session with its training name — the days-without-training anchor
     * (home-start-card.md §3.2). Same NULL-tail guard as [pagedRecentWithStats].
     */
    @Query(
        """
        SELECT s.uuid AS session_uuid,
               s.finished_at AS finished_at,
               t.name AS training_name,
               t.is_adhoc AS is_adhoc
        FROM session_table s
        INNER JOIN training_table t ON t.uuid = s.training_uuid
        WHERE s.state = 'FINISHED' AND s.finished_at IS NOT NULL
        ORDER BY s.finished_at DESC
        LIMIT 1
        """,
    )
    fun observeLastFinishedSession(): Flow<LastFinishedSessionRow?>

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
     * Every session row, unfiltered — both `IN_PROGRESS` and `FINISHED`. Snapshot export only,
     * never a user-facing read.
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

    /** Atomically creates a session plus its performed_exercise rows. */
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

    /** Atomically marks a session FINISHED at [finishedAt]; no-op when the session is gone. */
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
     * The set that holds the record for [exerciseUuid], or null. Eligibility [PR_ELIGIBILITY] and
     * ordering [PR_ORDER] are shared verbatim with the observe and batch variants.
     */
    @Query(PR_SINGLE_SQL)
    suspend fun getPersonalRecord(exerciseUuid: Uuid): PersonalRecordRow?

    /**
     * Reactive form of [getPersonalRecord] — same SQL, re-emitted whenever a participating table
     * changes.
     */
    @Query(PR_SINGLE_SQL)
    fun observePersonalRecord(exerciseUuid: Uuid): Flow<PersonalRecordRow?>

    /**
     * Reactive PR candidates across many exercises in one subscription, best-first per exercise.
     * GUARD: eligibility lives here — consumers take `.first()` per group and never re-filter.
     */
    @Query(PR_BATCH_SQL)
    fun observePersonalRecordsBatch(exerciseUuids: List<Uuid>): Flow<List<PersonalRecordRow>>

    /**
     * Top-N finished sessions by volume (`Σ weight × reps`) since [sinceMillis]; weightless
     * exercises and weight-null sets are excluded before the sum.
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
     * Date-ordered set list for [exerciseUuid] across finished sessions; each row carries its
     * parent session metadata so consumers group without a round trip per row.
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
