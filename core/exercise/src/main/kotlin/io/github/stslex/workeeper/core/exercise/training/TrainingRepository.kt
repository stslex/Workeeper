package io.github.stslex.workeeper.core.exercise.training

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface TrainingRepository {

    fun getTrainings(query: String): Flow<PagingData<TrainingDataModel>>

    fun getTrainingsUnique(query: String): Flow<PagingData<TrainingDataModel>>

    suspend fun searchTrainingsUnique(query: String, limit: Int): List<TrainingDataModel>

    suspend fun updateTraining(training: TrainingChangeDataModel)

    suspend fun removeTraining(uuid: String)

    suspend fun getTraining(uuid: String): TrainingDataModel?

    fun subscribeForTraining(uuid: String): Flow<TrainingDataModel>

    suspend fun removeAll(uuids: List<String>)

    suspend fun getTrainings(query: String, startDate: Long, endDate: Long): List<TrainingDataModel>
}
