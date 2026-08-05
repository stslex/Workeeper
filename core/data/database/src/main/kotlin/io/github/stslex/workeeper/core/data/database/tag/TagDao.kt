package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tag_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    /**
     * Auto-prune (D-OPEN-4): a tag with no remaining links — in EITHER link table — leaves
     * the dictionary. A global sweep rather than a per-diff prune, on purpose: it also
     * collects orphans left by paths that do not prune (an entity's permanent delete
     * CASCADEs its links away), so the dictionary self-heals on the next save.
     *
     * WHERE this runs is the ruling, not a convenience: on SAVE COMMIT only, inside the same
     * transaction as the link writes (`ExerciseRepositoryImpl.saveItem`,
     * `TrainingRepositoryImpl.updateTrainingWithPlans`) — never on tag creation and never on
     * an unlink-in-draft, so a freshly created, not-yet-saved tag survives until some editor
     * commits, and the editor that created it links it in the same transaction that sweeps.
     */
    @Query(
        """
        DELETE FROM tag_table
        WHERE uuid NOT IN (SELECT tag_uuid FROM exercise_tag_table)
          AND uuid NOT IN (SELECT tag_uuid FROM training_tag_table)
        """,
    )
    suspend fun deleteOrphans()
}
