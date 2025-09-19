package io.github.stslex.workeeper.feature.single_training.domain.interactor

import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel

internal interface SingleTrainingInteractor {

    suspend fun getTraining(uuid: String): TrainingDomainModel?

    suspend fun removeTraining(uuid: String)

    suspend fun updateTraining(training: TrainingDomainChangeModel)
}
