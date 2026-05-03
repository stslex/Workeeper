// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
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
        SELECT * FROM set_table
        WHERE performed_exercise_uuid = :performedExerciseUuid
          AND position = :position
        LIMIT 1
        """,
    )
    suspend fun getByPerformedAndPosition(performedExerciseUuid: Uuid, position: Int): SetEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM set_table
            WHERE performed_exercise_uuid = :performedExerciseUuid
        )
        """,
    )
    suspend fun hasAnyForPerformed(performedExerciseUuid: Uuid): Boolean

    @Query(
        """
        SELECT COUNT(*) FROM set_table
        WHERE performed_exercise_uuid = :performedExerciseUuid
        """,
    )
    suspend fun countByPerformedExercise(performedExerciseUuid: Uuid): Int

    @Deprecated(
        message = "v5 plan-first model: prev-set hint comes from training_exercise.plan_sets" +
            " or exercise.last_adhoc_sets, not from history. Kept temporarily so existing" +
            " call sites stay compiling until they migrate to the plan accessors.",
    )
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: SetEntity)

    @Update
    suspend fun update(set: SetEntity)

    @Query("DELETE FROM set_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Query(
        """
        DELETE FROM set_table
        WHERE performed_exercise_uuid = :performedExerciseUuid
          AND position = :position
        """,
    )
    suspend fun deleteByPerformedAndPosition(performedExerciseUuid: Uuid, position: Int)

    @Query(
        """
        DELETE FROM set_table
        WHERE performed_exercise_uuid = :performedExerciseUuid
        """,
    )
    suspend fun deleteAllForPerformedExercise(performedExerciseUuid: Uuid)
}
