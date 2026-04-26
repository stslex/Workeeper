package io.github.stslex.workeeper.core.database.training

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

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

    @Query("DELETE FROM training_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)
}
