// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel

interface TrainingExerciseRepository {

    suspend fun getPlan(trainingUuid: String, exerciseUuid: String): List<PlanSetDataModel>?

    /**
     * Plans for [exerciseUuids] within [trainingUuid]. GUARD: key presence IS the plan-attached
     * flag — absent key, null value and empty list are three states; read it with `containsKey`.
     */
    suspend fun getPlans(
        trainingUuid: String,
        exerciseUuids: List<String>,
    ): Map<String, List<PlanSetDataModel>?>

    suspend fun setPlan(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    )

    /**
     * Attachment IS the row: inserts a plan row at the training's next position with [planSets].
     * Read key presence via [getPlans] first — attaching over an existing row is the caller's bug.
     */
    suspend fun attachExercise(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    )

    /** See [attachExercise]. */
    suspend fun detachExercise(trainingUuid: String, exerciseUuid: String)

    /** The (exerciseUuid, position, planSets) tuples for a training, ordered by position. */
    suspend fun getRowsForTraining(trainingUuid: String): List<TrainingExerciseRow>

    data class TrainingExerciseRow(
        val exerciseUuid: String,
        val position: Int,
        val planSets: List<PlanSetDataModel>?,
    )
}
