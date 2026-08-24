package io.github.stslex.workeeper.core.data.database.exercise

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ExerciseDao {

    @Query(
        """
        SELECT * FROM exercise_table
        WHERE archived = 0 AND is_adhoc = 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun pagedActive(): PagingSource<Int, ExerciseEntity>

    /**
     * Paged active library exercises plus derived stats: finished-session count, linked-trainings
     * count and last-trained timestamp. Powers the all-exercises list footer line.
     */
    @Query(
        """
        SELECT e.*,
               (SELECT COUNT(DISTINCT pe.session_uuid)
                FROM performed_exercise_table pe
                INNER JOIN session_table sn ON sn.uuid = pe.session_uuid
                WHERE pe.exercise_uuid = e.uuid
                  AND sn.state = 'FINISHED'
                  AND sn.finished_at IS NOT NULL
                  AND pe.skipped = 0) AS session_count,
               (SELECT COUNT(DISTINCT te.training_uuid)
                FROM training_exercise_table te
                INNER JOIN training_table t ON t.uuid = te.training_uuid
                WHERE te.exercise_uuid = e.uuid
                  AND t.archived = 0
                  AND t.is_adhoc = 0) AS linked_trainings_count,
               (SELECT MAX(sn.finished_at)
                FROM session_table sn
                INNER JOIN performed_exercise_table pe ON pe.session_uuid = sn.uuid
                WHERE pe.exercise_uuid = e.uuid
                  AND sn.state = 'FINISHED'
                  AND sn.finished_at IS NOT NULL
                  AND pe.skipped = 0) AS last_trained_at
        FROM exercise_table e
        WHERE e.archived = 0 AND e.is_adhoc = 0
        ORDER BY e.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveWithStats(): PagingSource<Int, ExerciseListItemRow>

    /** Same projection as [pagedActiveWithStats], filtered to exercises with any of [tagUuids]. */
    @Query(
        """
        SELECT e.*,
               (SELECT COUNT(DISTINCT pe.session_uuid)
                FROM performed_exercise_table pe
                INNER JOIN session_table sn ON sn.uuid = pe.session_uuid
                WHERE pe.exercise_uuid = e.uuid
                  AND sn.state = 'FINISHED'
                  AND sn.finished_at IS NOT NULL
                  AND pe.skipped = 0) AS session_count,
               (SELECT COUNT(DISTINCT te.training_uuid)
                FROM training_exercise_table te
                INNER JOIN training_table t ON t.uuid = te.training_uuid
                WHERE te.exercise_uuid = e.uuid
                  AND t.archived = 0
                  AND t.is_adhoc = 0) AS linked_trainings_count,
               (SELECT MAX(sn.finished_at)
                FROM session_table sn
                INNER JOIN performed_exercise_table pe ON pe.session_uuid = sn.uuid
                WHERE pe.exercise_uuid = e.uuid
                  AND sn.state = 'FINISHED'
                  AND sn.finished_at IS NOT NULL
                  AND pe.skipped = 0) AS last_trained_at
        FROM exercise_table e
        INNER JOIN exercise_tag_table et ON et.exercise_uuid = e.uuid
        WHERE e.archived = 0 AND e.is_adhoc = 0 AND et.tag_uuid IN (:tagUuids)
        GROUP BY e.uuid
        ORDER BY e.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveWithStatsByTags(tagUuids: List<Uuid>): PagingSource<Int, ExerciseListItemRow>

    @Query(
        """
        SELECT * FROM exercise_table
        WHERE archived = 0 AND is_adhoc = 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    suspend fun getAllActive(): List<ExerciseEntity>

    @Query(
        """
        SELECT e.* FROM exercise_table e
        JOIN exercise_tag_table et ON et.exercise_uuid = e.uuid
        WHERE e.archived = 0 AND e.is_adhoc = 0 AND et.tag_uuid IN (:tagUuids)
        GROUP BY e.uuid
        ORDER BY e.name COLLATE NOCASE ASC
        """,
    )
    fun pagedActiveByTags(tagUuids: List<Uuid>): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercise_table WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun pagedArchived(): PagingSource<Int, ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercise_table WHERE archived = 1")
    fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM exercise_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): ExerciseEntity?

    /**
     * Case-insensitive single-row lookup by name — the inline-create flow surfaces the existing
     * match instead of tripping the unique-name constraint.
     */
    @Query("SELECT * FROM exercise_table WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<Uuid>): List<ExerciseEntity>

    /**
     * Every exercise row, unfiltered — includes `is_adhoc = 1` and `archived = 1`. Snapshot export
     * only; it needs the full library to stay referentially intact. Never a user-facing list.
     */
    @Query("SELECT * FROM exercise_table ORDER BY created_at ASC, uuid ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT uuid, last_adhoc_sets FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun getAdhocPlansBatch(uuids: List<Uuid>): List<ExerciseAdhocPlanRow>

    /**
     * Ad-hoc exercises performed in [sessionUuid], for the discard cascade. GUARD: join through
     * `performed_exercise_table` — a one-off exercise has no `training_exercise_table` row.
     */
    @Query(
        """
        SELECT e.* FROM exercise_table e
        INNER JOIN performed_exercise_table pe ON pe.exercise_uuid = e.uuid
        WHERE pe.session_uuid = :sessionUuid AND e.is_adhoc = 1
        """,
    )
    suspend fun getAdhocExercisesForSession(sessionUuid: Uuid): List<ExerciseEntity>

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("UPDATE exercise_table SET last_adhoc_sets = :lastAdhocSets WHERE uuid = :uuid")
    suspend fun updateLastAdhocSets(uuid: Uuid, lastAdhocSets: String?)

    @Query("UPDATE exercise_table SET type = :type WHERE uuid = :uuid")
    suspend fun updateType(uuid: Uuid, type: ExerciseTypeEntity)

    @Query("UPDATE exercise_table SET archived = 1, archived_at = :archivedAt WHERE uuid = :uuid")
    suspend fun archive(uuid: Uuid, archivedAt: Long)

    @Query("UPDATE exercise_table SET archived = 0, archived_at = NULL WHERE uuid = :uuid")
    suspend fun restore(uuid: Uuid)

    @Query("DELETE FROM exercise_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)

    @Query("DELETE FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<Uuid>)

    /**
     * Deletes [uuid] only when it is an ad-hoc exercise nothing references any more. GUARD: the
     * `training_exercise_table` check is load-bearing — RESTRICT there kills the whole transaction.
     */
    @Query(
        """
        DELETE FROM exercise_table
        WHERE uuid = :uuid AND is_adhoc = 1
          AND NOT EXISTS (
              SELECT 1 FROM performed_exercise_table WHERE exercise_uuid = :uuid
          )
          AND NOT EXISTS (
              SELECT 1 FROM training_exercise_table WHERE exercise_uuid = :uuid
          )
        """,
    )
    suspend fun deleteIfAdhocOrphan(uuid: Uuid)

    /**
     * Flips `is_adhoc` to 0 for every ad-hoc exercise performed in [sessionUuid], inside the
     * `finishSession` transaction. GUARD: membership comes from `performed_exercise_table`.
     */
    @Query(
        """
        UPDATE exercise_table SET is_adhoc = 0
        WHERE is_adhoc = 1
          AND uuid IN (
              SELECT pe.exercise_uuid FROM performed_exercise_table pe
              WHERE pe.session_uuid = :sessionUuid
          )
        """,
    )
    suspend fun graduateAdhocForSession(sessionUuid: Uuid)

    /**
     * UUID of the exercise from the most recently finished session, `null` on a fresh install —
     * the charts default selection.
     */
    @Query(
        """
        SELECT pe.exercise_uuid AS uuid
        FROM performed_exercise_table pe
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        WHERE sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
        ORDER BY sn.finished_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLastTrainedExerciseUuid(): Uuid?

    /**
     * Distinct active (non-adhoc, non-archived) trainings that include [exerciseUuid]. Surfaced as
     * `linkedTrainingsCount` on the all-exercises list footer.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT te.training_uuid)
        FROM training_exercise_table te
        INNER JOIN training_table t ON t.uuid = te.training_uuid
        WHERE te.exercise_uuid = :exerciseUuid
          AND t.archived = 0
          AND t.is_adhoc = 0
        """,
    )
    fun observeLinkedTrainingsCount(exerciseUuid: Uuid): Flow<Int>

    /**
     * Timestamp of the most recently finished session that logged a set for [exerciseUuid], `null`
     * when none. Surfaced as `lastTrainedAt` on the all-exercises list footer.
     */
    @Query(
        """
        SELECT MAX(sn.finished_at)
        FROM session_table sn
        INNER JOIN performed_exercise_table pe ON pe.session_uuid = sn.uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
          AND pe.skipped = 0
        """,
    )
    fun observeLastTrainedAt(exerciseUuid: Uuid): Flow<Long?>

    /**
     * Active exercises with at least one logged set in a finished session, most recent first. Scope
     * matches `SessionDao.getHistoryByExercise` so a picker entry never routes to an empty chart.
     */
    @Query(
        """
        SELECT e.uuid AS uuid,
               e.name AS name,
               e.type AS type,
               MAX(sn.finished_at) AS last_finished_at
        FROM exercise_table e
        JOIN performed_exercise_table pe ON pe.exercise_uuid = e.uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        WHERE sn.state = 'FINISHED'
          AND sn.finished_at IS NOT NULL
          AND e.archived = 0
          AND e.is_adhoc = 0
          AND pe.skipped = 0
          AND EXISTS (
              SELECT 1 FROM set_table s WHERE s.performed_exercise_uuid = pe.uuid
          )
        GROUP BY e.uuid
        ORDER BY last_finished_at DESC
        """,
    )
    suspend fun getRecentlyTrainedExercises(): List<RecentTrainedExerciseRow>
}
