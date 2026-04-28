package io.github.stslex.workeeper.core.database.training

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
@Dao
interface TrainingDao {

    @Query(
        """
        SELECT * FROM training_table
        WHERE is_adhoc = 0 AND archived = 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun pagedTemplates(): PagingSource<Int, TrainingEntity>

    @Query(
        """
        SELECT t.* FROM training_table t
        JOIN training_tag_table tt ON tt.training_uuid = t.uuid
        WHERE t.is_adhoc = 0 AND t.archived = 0 AND tt.tag_uuid IN (:tagUuids)
        GROUP BY t.uuid
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun pagedTemplatesByTags(tagUuids: List<Uuid>): PagingSource<Int, TrainingEntity>

    @Query(
        """
        SELECT t.uuid, t.name, t.description, t.is_adhoc, t.archived, t.created_at, t.archived_at,
               (SELECT COUNT(*) FROM training_exercise_table WHERE training_uuid = t.uuid) AS exercise_count,
               (SELECT MAX(s.finished_at) FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'FINISHED') AS last_session_at,
               (SELECT s.uuid FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_uuid,
               (SELECT s.started_at FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_started_at
        FROM training_table t
        WHERE t.archived = 0 AND t.is_adhoc = 0
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveWithStats(): PagingSource<Int, TrainingListItemRow>

    @Query(
        """
        SELECT t.uuid, t.name, t.description, t.is_adhoc, t.archived, t.created_at, t.archived_at,
               (SELECT COUNT(*) FROM training_exercise_table WHERE training_uuid = t.uuid) AS exercise_count,
               (SELECT MAX(s.finished_at) FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'FINISHED') AS last_session_at,
               (SELECT s.uuid FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_uuid,
               (SELECT s.started_at FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_started_at
        FROM training_table t
        WHERE t.archived = 0 AND t.is_adhoc = 0
          AND EXISTS (
            SELECT 1 FROM training_tag_table tt
            WHERE tt.training_uuid = t.uuid AND tt.tag_uuid IN (:tagUuids)
          )
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveWithStatsByTags(tagUuids: List<Uuid>): PagingSource<Int, TrainingListItemRow>

    @Query(
        """
        SELECT t.uuid, t.name, t.description, t.is_adhoc, t.archived, t.created_at, t.archived_at,
               (SELECT COUNT(*) FROM training_exercise_table WHERE training_uuid = t.uuid) AS exercise_count,
               (SELECT MAX(s.finished_at) FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'FINISHED') AS last_session_at,
               (SELECT s.uuid FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_uuid,
               (SELECT s.started_at FROM session_table s
                  WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_started_at
        FROM training_table t
        WHERE t.archived = 0 AND t.is_adhoc = 0
        ORDER BY (last_session_at IS NULL), last_session_at DESC, t.name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeRecentTemplates(limit: Int): Flow<List<TrainingListItemRow>>

    @Query("SELECT * FROM training_table WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun pagedArchived(): PagingSource<Int, TrainingEntity>

    @Query("SELECT COUNT(*) FROM training_table WHERE archived = 1")
    fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM training_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): TrainingEntity?

    @Query("SELECT * FROM training_table WHERE uuid = :uuid")
    fun observeById(uuid: Uuid): Flow<TrainingEntity?>

    @Insert
    suspend fun insert(training: TrainingEntity)

    @Update
    suspend fun update(training: TrainingEntity)

    @Query("UPDATE training_table SET archived = 1, archived_at = :archivedAt WHERE uuid = :uuid")
    suspend fun archive(uuid: Uuid, archivedAt: Long)

    @Query("UPDATE training_table SET archived = 0, archived_at = NULL WHERE uuid = :uuid")
    suspend fun restore(uuid: Uuid)

    @Query(
        """
        UPDATE training_table SET archived = 1, archived_at = :archivedAt
        WHERE uuid IN (:uuids)
        """,
    )
    suspend fun archiveAll(uuids: List<Uuid>, archivedAt: Long)

    @Query("DELETE FROM training_table WHERE uuid IN (:uuids)")
    suspend fun permanentDeleteAll(uuids: List<Uuid>)

    @Query("DELETE FROM training_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)
}
