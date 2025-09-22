package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel

interface ExerciseInteractor {

    suspend fun saveItem(item: ExerciseChangeDataModel)

    suspend fun deleteItem(uuid: String)

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun searchItems(query: String): List<ExerciseDataModel>

}
