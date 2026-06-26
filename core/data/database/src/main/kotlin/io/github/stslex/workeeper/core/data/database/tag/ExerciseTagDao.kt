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
     * Every (exercise, tag-name) pair in the database, one [ExerciseTagNameRow] per pair
     * (snapshot export); the caller groups by `exerciseUuid` in memory. A full-table join
     * with no uuid binding, so it cannot hit `SQLITE_MAX_VARIABLE_NUMBER` for large libraries
     * (API 28-30 ship SQLite < 3.32 where the host-variable limit is 999), and it matches the
     * unfiltered `getAll()` readers the exporter consumes in the same pass.
     */
    @Query(
        """
        SELECT et.exercise_uuid AS exercise_uuid, t.name AS name
        FROM exercise_tag_table et
        JOIN tag_table t ON t.uuid = et.tag_uuid
        ORDER BY et.exercise_uuid, t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getAllExerciseTagNames(): List<ExerciseTagNameRow>

    @Insert
    suspend fun insert(rows: List<ExerciseTagEntity>)

    @Query("DELETE FROM exercise_tag_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun deleteByExercise(exerciseUuid: Uuid)
}
