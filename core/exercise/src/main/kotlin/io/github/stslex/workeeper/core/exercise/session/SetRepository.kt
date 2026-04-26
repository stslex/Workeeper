package io.github.stslex.workeeper.core.exercise.session

import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel

interface SetRepository {

    suspend fun getByPerformedExercise(performedExerciseUuid: String): List<SetsDataModel>

    suspend fun getLastFinishedSet(exerciseUuid: String): SetsDataModel?

    suspend fun insert(performedExerciseUuid: String, position: Int, set: SetsDataModel)

    suspend fun update(performedExerciseUuid: String, position: Int, set: SetsDataModel)

    suspend fun delete(uuid: String)
}
