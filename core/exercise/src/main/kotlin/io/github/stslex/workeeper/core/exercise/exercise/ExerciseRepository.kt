package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>>

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

    suspend fun saveItem(item: ChangeExerciseDataModel)

    suspend fun deleteItem(uuid: String)

    suspend fun searchItems(query: String): List<ExerciseDataModel>

    suspend fun deleteAllItems(uuids: List<Uuid>)

}