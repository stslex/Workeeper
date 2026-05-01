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

    @Query(
        """
        SELECT * FROM exercise_table
        WHERE archived = 0 AND is_adhoc = 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun pagedActive(): PagingSource<Int, ExerciseEntity>

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

    @Query("SELECT * FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<Uuid>): List<ExerciseEntity>

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

    @Query("UPDATE exercise_table SET archived = 1, archived_at = :archivedAt WHERE uuid = :uuid")
    suspend fun archive(uuid: Uuid, archivedAt: Long)

    @Query("UPDATE exercise_table SET archived = 0, archived_at = NULL WHERE uuid = :uuid")
    suspend fun restore(uuid: Uuid)

    @Query("DELETE FROM exercise_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)

    @Query("DELETE FROM exercise_table WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<Uuid>)

    /**
     * Flip `is_adhoc` to `0` for every exercise plan row attached to [trainingUuid]. Called
     * inside the `finishSession` transaction so inline-created ad-hoc exercises graduate to
     * regular library entries the moment the session is preserved.
     */
    @Query(
        """
        UPDATE exercise_table SET is_adhoc = 0
        WHERE uuid IN (
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
     * Active exercises that have at least one logged set in a finished session, ordered by
     * the most recent finish. Skipped performed_exercise rows and rows without any
     * `set_table` entries are excluded so the picker matches the scope of
     * [io.github.stslex.workeeper.core.database.session.SessionDao.getHistoryByExercise]
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
