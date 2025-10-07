package io.github.stslex.workeeper.feature.single_training.domain.interactor

import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import kotlinx.coroutines.flow.Flow

internal interface SingleTrainingInteractor {

    suspend fun getTraining(uuid: String): TrainingDomainModel?

    fun subscribeForTraining(uuid: String): Flow<TrainingDomainModel>

    suspend fun removeTraining(uuid: String)

    suspend fun updateTraining(training: TrainingDomainChangeModel)

    suspend fun searchTrainings(query: String): List<TrainingDomainModel>
}
