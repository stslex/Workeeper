package io.github.stslex.workeeper.core.database.tag

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

    @Insert
    suspend fun insert(rows: List<ExerciseTagEntity>)

    @Query("DELETE FROM exercise_tag_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun deleteByExercise(exerciseUuid: Uuid)
}
