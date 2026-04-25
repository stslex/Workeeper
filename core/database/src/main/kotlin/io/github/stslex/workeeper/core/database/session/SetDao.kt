package io.github.stslex.workeeper.core.database.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.stslex.workeeper.core.database.session.model.SetEntity
import kotlin.uuid.Uuid

@Dao
interface SetDao {

    @Query(
        """
        SELECT * FROM set_table
        WHERE performed_exercise_uuid = :performedExerciseUuid
        ORDER BY position ASC
        """,
    )
    suspend fun getByPerformedExercise(performedExerciseUuid: Uuid): List<SetEntity>

    @Query(
        """
        SELECT s.* FROM set_table s
        JOIN performed_exercise_table pe ON pe.uuid = s.performed_exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND sn.state = 'FINISHED'
        ORDER BY sn.finished_at DESC, s.position DESC
        LIMIT 1
        """,
    )
    suspend fun getLastFinishedSet(exerciseUuid: Uuid): SetEntity?

    @Insert
    suspend fun insert(set: SetEntity)

    @Update
    suspend fun update(set: SetEntity)

    @Query("DELETE FROM set_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)
}
