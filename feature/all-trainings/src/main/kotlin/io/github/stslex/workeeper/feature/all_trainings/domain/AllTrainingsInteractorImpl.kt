package io.github.stslex.workeeper.feature.all_trainings.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class AllTrainingsInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    @param:DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
) : AllTrainingsInteractor {

    override suspend fun deleteAll(trainingsUuids: List<String>) {
        withContext(defaultDispatcher) {
            exerciseRepository.deleteByTrainingsUuids(trainingsUuids)
            trainingRepository.removeAll(trainingsUuids)
        }
    }
}
