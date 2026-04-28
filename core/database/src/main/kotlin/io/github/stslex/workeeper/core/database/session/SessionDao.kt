package io.github.stslex.workeeper.core.database.session

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

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

    @Query(
        """
        SELECT * FROM session_table
        WHERE state = 'FINISHED'
        ORDER BY finished_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

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
        LIMIT :limit
        """,
    )
    fun observeRecentWithStats(limit: Int): Flow<List<RecentSessionRow>>

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
     * Heaviest set the user has ever logged for [exerciseUuid] across finished sessions.
     * The `:isWeightless` flag controls whether weight participates in the ordering — for
     * weightless exercises only reps and timestamp drive PR ownership. Tiebreak by earliest
     * `finished_at` so the PR badge belongs to the first occurrence.
     */
    @Query(
        """
        SELECT s.uuid AS set_uuid,
               s.weight AS weight,
               s.reps AS reps,
               s.type AS type,
               s.performed_exercise_uuid AS performed_exercise_uuid,
               sn.uuid AS session_uuid,
               sn.finished_at AS finished_at
        FROM set_table s
        JOIN performed_exercise_table pe ON pe.uuid = s.performed_exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
          AND (:isWeightless = 1 OR s.weight IS NOT NULL)
        ORDER BY
            CASE WHEN :isWeightless = 0 THEN s.weight END DESC,
            s.reps DESC,
            sn.finished_at ASC,
            s.position ASC
        LIMIT 1
        """,
    )
    suspend fun getPersonalRecord(exerciseUuid: Uuid, isWeightless: Boolean): PersonalRecordRow?

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
