package io.github.stslex.workeeper.core.database.training

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlin.uuid.Uuid

@Dao
interface TrainingExerciseDao {

    @Query(
        """
        SELECT * FROM training_exercise_table
        WHERE training_uuid = :trainingUuid
        ORDER BY position ASC
        """,
    )
    suspend fun getByTraining(trainingUuid: Uuid): List<TrainingExerciseEntity>

    @Query(
        """
        SELECT * FROM training_exercise_table
        WHERE exercise_uuid = :exerciseUuid
        """,
    )
    suspend fun getAllForExercise(exerciseUuid: Uuid): List<TrainingExerciseEntity>

    @Query(
        """
        SELECT COUNT(*) FROM training_exercise_table te
        JOIN training_table t ON t.uuid = te.training_uuid
        WHERE te.exercise_uuid = :exerciseUuid
          AND t.archived = 0
          AND t.is_adhoc = 0
        """,
    )
    suspend fun countActiveTemplatesUsing(exerciseUuid: Uuid): Int

    @Query(
        """
        SELECT t.name FROM training_exercise_table te
        JOIN training_table t ON t.uuid = te.training_uuid
        WHERE te.exercise_uuid = :exerciseUuid
          AND t.archived = 0
          AND t.is_adhoc = 0
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getActiveTemplateNamesUsing(exerciseUuid: Uuid): List<String>

    @Query(
        """
        SELECT plan_sets FROM training_exercise_table
        WHERE training_uuid = :trainingUuid AND exercise_uuid = :exerciseUuid
        """,
    )
    suspend fun getPlanSets(trainingUuid: Uuid, exerciseUuid: Uuid): String?

    @Query(
        """
        UPDATE training_exercise_table
        SET plan_sets = :planSets
        WHERE training_uuid = :trainingUuid AND exercise_uuid = :exerciseUuid
        """,
    )
    suspend fun updatePlanSets(trainingUuid: Uuid, exerciseUuid: Uuid, planSets: String?)

    @Insert
    suspend fun insert(rows: List<TrainingExerciseEntity>)

    @Query("DELETE FROM training_exercise_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)
}
