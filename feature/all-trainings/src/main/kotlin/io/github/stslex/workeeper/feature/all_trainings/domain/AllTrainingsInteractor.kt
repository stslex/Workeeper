package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import kotlinx.coroutines.flow.Flow

internal interface AllTrainingsInteractor {

    suspend fun deleteAll(trainingsUuids: List<String>)

    fun getTrainings(query: String): Flow<PagingData<TrainingDataModel>>
}
