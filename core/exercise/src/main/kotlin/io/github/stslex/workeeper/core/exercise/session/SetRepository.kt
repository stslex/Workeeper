package io.github.stslex.workeeper.core.exercise.session

import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel

interface SetRepository {

    suspend fun getByPerformedExercise(performedExerciseUuid: String): List<SetsDataModel>

    @Deprecated(
        message = "v5 plan-first model: prev-set hint comes from training_exercise.plan_sets" +
            " or exercise.last_adhoc_sets, not from history. Kept temporarily until existing" +
            " call sites migrate.",
    )
    suspend fun getLastFinishedSet(exerciseUuid: String): SetsDataModel?

    suspend fun insert(performedExerciseUuid: String, position: Int, set: SetsDataModel)

    suspend fun update(performedExerciseUuid: String, position: Int, set: SetsDataModel)

    suspend fun delete(uuid: String)
}
