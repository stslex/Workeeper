package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tag_table ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT * FROM tag_table
        WHERE name LIKE :prefix || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    suspend fun searchByPrefix(prefix: String): List<TagEntity>

    @Query("SELECT * FROM tag_table WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    /** Longest-idle tags for «Отставшие группы»; never-trained tags produce no row. */
    @Query(
        """
        SELECT tg.name AS tag_name, MAX(sn.finished_at) AS last_trained_at
        FROM tag_table tg
        JOIN exercise_tag_table et ON et.tag_uuid = tg.uuid
        JOIN performed_exercise_table pe ON pe.exercise_uuid = et.exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        WHERE sn.state = 'FINISHED' AND sn.finished_at IS NOT NULL AND pe.skipped = 0
        GROUP BY tg.uuid
        ORDER BY last_trained_at ASC, tg.name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeTagIdleStats(limit: Int): Flow<List<TagIdleRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>)

    /** Global orphan sweep; runs on save commit only, inside the link-write transaction. */
    @Query(
        """
        DELETE FROM tag_table
        WHERE uuid NOT IN (SELECT tag_uuid FROM exercise_tag_table)
          AND uuid NOT IN (SELECT tag_uuid FROM training_tag_table)
        """,
    )
    suspend fun deleteOrphans()
}
