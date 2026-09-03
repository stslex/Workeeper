package io.github.stslex.workeeper.core.data.database.session

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
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

    /** Unfiltered full-graph read; its ORDER BY is the export contract. Not for user lists. */
    @Query(
        """
        SELECT * FROM performed_exercise_table
        ORDER BY session_uuid, position ASC
        """,
    )
    suspend fun getAll(): List<PerformedExerciseEntity>

    @Insert
    suspend fun insert(rows: List<PerformedExerciseEntity>)

    @Insert
    suspend fun insert(row: PerformedExerciseEntity)

    /** Highest `position` inside [sessionUuid], or `null` when it has no performed rows. */
    @Query(
        """
        SELECT MAX(position) FROM performed_exercise_table
        WHERE session_uuid = :sessionUuid
        """,
    )
    suspend fun getMaxPosition(sessionUuid: Uuid): Int?

    @Query("UPDATE performed_exercise_table SET skipped = :skipped WHERE uuid = :uuid")
    suspend fun setSkipped(uuid: Uuid, skipped: Boolean)

    /** Removes one performed exercise from its session (v3 §6.1 "deleted"). */
    @Query("DELETE FROM performed_exercise_table WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: Uuid)
}
