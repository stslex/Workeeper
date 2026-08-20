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

    /**
     * The longest-idle tags: for each tag, the latest `finished_at` among finished sessions
     * whose performed exercises carry it — «Отставшие группы» (home-start-card.md §3.3).
     * The spec's path is `performed_exercise → exercise → exercise_tag → tag`;
     * `exercise_table` itself contributes no predicate, and both link FKs guarantee the row
     * exists, so the join goes `performed_exercise → exercise_tag` directly.
     *
     * `pe.skipped = 0` because a skipped exercise was not performed — its session finishing
     * says nothing about when that muscle group last trained (same reading of the flag as
     * `pagedRecentWithStats`' exercise count). Never-trained tags produce no row (INNER
     * joins), deliberately: HD1's never-run-first ruling names templates, and a tag with no
     * history has no day count for the bar or the right-edge number to show.
     */
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
