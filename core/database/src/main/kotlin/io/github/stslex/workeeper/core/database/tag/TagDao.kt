package io.github.stslex.workeeper.core.database.tag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("DELETE FROM tag_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)
}
