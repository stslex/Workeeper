package io.github.stslex.workeeper.core.exercise.data

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.data.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    suspend fun saveItem(item: ChangeExerciseDataModel)

    suspend fun deleteItem(uuid: String)

}
