package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun getExerciseByName(name: String): ExerciseDataModel?

    fun getExercises(
        name: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<ExerciseDataModel>>

    fun getExercisesExactly(
        name: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<ExerciseDataModel>>

    suspend fun saveItem(item: ExerciseChangeDataModel)

    suspend fun deleteItem(uuid: String)

    suspend fun searchItems(query: String): List<ExerciseDataModel>

    suspend fun deleteAllItems(uuids: List<Uuid>)

    suspend fun deleteByTrainingUuid(trainingUuid: String)

    suspend fun deleteByTrainingsUuids(trainingsUuids: List<String>)

}