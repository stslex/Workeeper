package io.github.stslex.workeeper.core.data.database.training

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
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
    SELECT exercise_uuid, plan_sets FROM training_exercise_table
    WHERE training_uuid = :trainingUuid AND exercise_uuid IN (:exerciseUuids)
    """,
    )
    suspend fun getPlanSetsBatch(
        trainingUuid: Uuid,
        exerciseUuids: List<Uuid>,
    ): List<TrainingExercisePlanRow>

    /** Unfiltered full-graph read; its ORDER BY is the export contract. Not for user lists. */
    @Query(
        """
        SELECT * FROM training_exercise_table
        ORDER BY training_uuid, position ASC
        """,
    )
    suspend fun getAll(): List<TrainingExerciseEntity>

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

    @Insert
    suspend fun insert(row: TrainingExerciseEntity)

    /** Highest `position` inside [trainingUuid], or `null` when it has no plan rows. */
    @Query(
        """
        SELECT MAX(position) FROM training_exercise_table
        WHERE training_uuid = :trainingUuid
        """,
    )
    suspend fun getMaxPosition(trainingUuid: Uuid): Int?

    @Query("DELETE FROM training_exercise_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)

    /** Detaches one exercise from a training's plan (v3 §6.2); idempotent. */
    @Query(
        """
        DELETE FROM training_exercise_table
        WHERE training_uuid = :trainingUuid AND exercise_uuid = :exerciseUuid
        """,
    )
    suspend fun deleteByTrainingAndExercise(trainingUuid: Uuid, exerciseUuid: Uuid)
}
