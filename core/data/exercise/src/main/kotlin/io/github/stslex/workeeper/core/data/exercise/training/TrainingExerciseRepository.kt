// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.training

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel

interface TrainingExerciseRepository {

    suspend fun getPlan(trainingUuid: String, exerciseUuid: String): List<PlanSetDataModel>?

    suspend fun setPlan(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    )

    /**
     * Returns the (exerciseUuid, position, plan_sets) tuples for a training, ordered by
     * position. Lets callers join with the exercise table without owning a Dao reference
     * directly.
     */
    suspend fun getRowsForTraining(trainingUuid: String): List<TrainingExerciseRow>

    data class TrainingExerciseRow(
        val exerciseUuid: String,
        val position: Int,
        val planSets: List<PlanSetDataModel>?,
    )
}
