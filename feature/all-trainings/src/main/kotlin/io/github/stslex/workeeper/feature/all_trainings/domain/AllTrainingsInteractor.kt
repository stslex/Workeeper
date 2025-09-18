package io.github.stslex.workeeper.feature.all_trainings.domain

internal interface AllTrainingsInteractor {

    suspend fun deleteAll(trainingsUuids: List<String>)
}
