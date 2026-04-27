package io.github.stslex.workeeper.core.database.exercise

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise_table WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun pagedActive(): PagingSource<Int, ExerciseEntity>

    @Query(
        """
        SELECT e.* FROM exercise_table e
        JOIN exercise_tag_table et ON et.exercise_uuid = e.uuid
        WHERE e.archived = 0 AND et.tag_uuid IN (:tagUuids)
        GROUP BY e.uuid
        ORDER BY e.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveByTags(tagUuids: List<Uuid>): PagingSource<Int, ExerciseEntity>

    /**
     * AND-semantics tag filter: only exercises tagged with EVERY tag in `tagUuids`.
     * Currently unused — the library tab switched to OR semantics via
     * [pagedActiveByTags]. Retained until a future feature needs the AND filter
     * again.
     */
    @Deprecated(
        message = "OR-semantics is the v1 default; switch back to pagedActiveByTags.",
        replaceWith = ReplaceWith("pagedActiveByTags(tagUuids)"),
    )
    @Query(
        """
        SELECT e.* FROM exercise_table e
        WHERE e.archived = 0
          AND (
            SELECT COUNT(DISTINCT et.tag_uuid)
            FROM exercise_tag_table et
            WHERE et.exercise_uuid = e.uuid AND et.tag_uuid IN (:tagUuids)
          ) = :tagCount
        ORDER BY e.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveByAllTags(
        tagUuids: List<Uuid>,
        tagCount: Int,
    ): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercise_table WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun pagedArchived(): PagingSource<Int, ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercise_table WHERE archived = 1")
    fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM exercise_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): ExerciseEntity?

    @Query("SELECT * FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<Uuid>): List<ExerciseEntity>

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("UPDATE exercise_table SET archived = 1, archived_at = :archivedAt WHERE uuid = :uuid")
    suspend fun archive(uuid: Uuid, archivedAt: Long)

    @Query("UPDATE exercise_table SET archived = 0, archived_at = NULL WHERE uuid = :uuid")
    suspend fun restore(uuid: Uuid)

    @Query("DELETE FROM exercise_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)
}
