package io.github.stslex.workeeper.core.database.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlin.uuid.Uuid

@Dao
interface PerformedExerciseDao {

    @Query(
        """
        SELECT * FROM performed_exercise_table
        WHERE session_uuid = :sessionUuid
        ORDER BY position ASC
        """,
    )
    suspend fun getBySession(sessionUuid: Uuid): List<PerformedExerciseEntity>

    @Insert
    suspend fun insert(rows: List<PerformedExerciseEntity>)

    @Query("UPDATE performed_exercise_table SET skipped = :skipped WHERE uuid = :uuid")
    suspend fun setSkipped(uuid: Uuid, skipped: Boolean)
}
