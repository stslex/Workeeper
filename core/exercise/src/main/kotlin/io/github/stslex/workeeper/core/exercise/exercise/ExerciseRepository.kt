package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    fun getUniqueExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    suspend fun getExercisesByUuid(uuids: List<String>): List<ExerciseDataModel>

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun getExercises(name: String, startDate: Long, endDate: Long): List<ExerciseDataModel>

    suspend fun saveItem(item: ExerciseChangeDataModel)

    suspend fun deleteItem(uuid: String)

    suspend fun searchItemsWithExclude(query: String): List<ExerciseDataModel>

    suspend fun deleteAllItems(uuids: List<Uuid>)

    suspend fun deleteByTrainingUuid(trainingUuid: String)

    suspend fun deleteByTrainingsUuids(trainingsUuids: List<String>)
}
