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
}
