package io.github.stslex.workeeper.core.database.session

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface SessionDao {

    @Query("SELECT * FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

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
}
