package io.github.stslex.workeeper.core.data.database.tag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlin.uuid.Uuid

@Dao
interface ExerciseTagDao {

    @Query("SELECT tag_uuid FROM exercise_tag_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun getTagUuids(exerciseUuid: Uuid): List<Uuid>

    @Query(
        """
        SELECT t.name FROM tag_table t
        JOIN exercise_tag_table et ON et.tag_uuid = t.uuid
        WHERE et.exercise_uuid = :exerciseUuid
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getTagNames(exerciseUuid: Uuid): List<String>

    /**
     * Denormalized tag names for many exercises in one query (snapshot export).
     * Returns one [ExerciseTagNameRow] per (exercise, tag) pair; the caller groups
     * by `exerciseUuid` in memory. Mirrors the `getPlanSetsBatch` batch convention;
     * callers must short-circuit an empty [exerciseUuids] list (Room renders `IN ()`).
     */
    @Query(
        """
        SELECT et.exercise_uuid AS exercise_uuid, t.name AS name
        FROM exercise_tag_table et
        JOIN tag_table t ON t.uuid = et.tag_uuid
        WHERE et.exercise_uuid IN (:exerciseUuids)
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getTagNamesForExercises(exerciseUuids: List<Uuid>): List<ExerciseTagNameRow>

    @Insert
    suspend fun insert(rows: List<ExerciseTagEntity>)

    @Query("DELETE FROM exercise_tag_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun deleteByExercise(exerciseUuid: Uuid)
}
