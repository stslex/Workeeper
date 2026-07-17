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
     * Paged active library exercises joined with derived stats: session count
     * (distinct finished sessions where the exercise was logged), linked-trainings count
     * (distinct active templates referencing the exercise), and last-trained timestamp.
     * Powers the v2.4 footer line on the all-exercises list. Mirror of [pagedActive] +
     * three correlated subqueries; Room invalidates whenever any of the joined tables
     * mutates. (v2.4 E6.)
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

    /**
     * Same projection as [pagedActiveWithStats] filtered to exercises tagged with any of
     * [tagUuids] (OR semantics, matches the legacy [pagedActiveByTags] contract).
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
     * Case-insensitive single-row lookup by name. Used by the inline-create flow to avoid
     * tripping the unique-name constraint when the user types a name that already matches
     * an existing library exercise — we surface that one rather than raising an error.
     */
    @Query("SELECT * FROM exercise_table WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<Uuid>): List<ExerciseEntity>

    /**
     * Every exercise row, unfiltered — includes `is_adhoc = 1` and `archived = 1`.
     * Documented exception to the `is_adhoc = 0` list invariant: the sole caller is
     * the AI-readable snapshot export, which needs the full library (adhoc exercises
     * are referenced by sessions, so omitting them would break referential integrity
     * in the export). Never use this for a user-facing list.
     */
    @Query("SELECT * FROM exercise_table ORDER BY created_at ASC, uuid ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT uuid, last_adhoc_sets FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun getAdhocPlansBatch(uuids: List<Uuid>): List<ExerciseAdhocPlanRow>

    /**
     * Ad-hoc exercises (`is_adhoc = 1`) currently joined to [trainingUuid] via the
     * `training_exercise_table` plan rows. Used by `discardAdhocSession` to cascade-delete
     * inline-created exercises when a Quick start / Track Now session is discarded. The
     * defence-in-depth predicate (flag AND join) ensures library exercises just picked
     * into the session — whose `is_adhoc` is `false` — are never deleted.
     */
    @Query(
        """
        SELECT e.* FROM exercise_table e
        INNER JOIN training_exercise_table te ON te.exercise_uuid = e.uuid
        WHERE te.training_uuid = :trainingUuid AND e.is_adhoc = 1
        """,
    )
    suspend fun getAdhocExercisesForTraining(trainingUuid: Uuid): List<ExerciseEntity>

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
     * Flip `is_adhoc` to `0` for every ad-hoc exercise plan row attached to [trainingUuid].
     * Called inside the `finishSession` transaction so inline-created ad-hoc exercises
     * graduate to regular library entries the moment the session is preserved. The
     * `is_adhoc = 1` predicate excludes library exercises that were merely picked into the
     * session — they are already at `is_adhoc = 0` and would only generate redundant writes.
     */
    @Query(
        """
        UPDATE exercise_table SET is_adhoc = 0
        WHERE is_adhoc = 1
          AND uuid IN (
              SELECT te.exercise_uuid FROM training_exercise_table te
              WHERE te.training_uuid = :trainingUuid
          )
        """,
    )
    suspend fun graduateAdhocForTraining(trainingUuid: Uuid)

    /**
     * UUID of the exercise from the most recently finished session. `null` when no finished
     * session exists (fresh install). Powers the v2.2 charts default selection when the
     * caller passes no explicit exercise.
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
     * Number of distinct active library trainings (non-adhoc, non-archived) that include
     * [exerciseUuid] via `training_exercise_table`. Surfaced as `linkedTrainingsCount`
     * on the all-exercises list footer ("in M trainings"). Flow-backed; Room invalidates
     * when either table changes. (v2.4 F1.)
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
     * Timestamp of the most recently finished session that logged at least one set for
     * [exerciseUuid] via a non-skipped performed-exercise row. `null` when no such
     * session exists. Surfaced as `lastTrainedAt` on the all-exercises list footer
     * ("last 4d ago"). Flow-backed. (v2.4 F2.)
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
     * Active exercises that have at least one logged set in a finished session, ordered by
     * the most recent finish. Skipped performed_exercise rows and rows without any
     * `set_table` entries are excluded so the picker matches the scope of
     * [io.github.stslex.workeeper.core.data.database.session.SessionDao.getHistoryByExercise]
     * exactly — a picker entry never routes to an empty chart. Powers the v2.2 chart
     * picker.
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
