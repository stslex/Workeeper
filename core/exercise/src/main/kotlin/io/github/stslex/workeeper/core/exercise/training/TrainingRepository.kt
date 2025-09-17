package io.github.stslex.workeeper.core.exercise.training

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface TrainingRepository {

    fun getTrainings(query: String): Flow<PagingData<TrainingDataModel>>

    suspend fun addTraining(training: TrainingDataModel)

    suspend fun updateTraining(training: TrainingChangeDataModel)

    suspend fun removeTrainings(uuids: Set<String>)

    suspend fun getTraining(uuid: String): TrainingDataModel
}
