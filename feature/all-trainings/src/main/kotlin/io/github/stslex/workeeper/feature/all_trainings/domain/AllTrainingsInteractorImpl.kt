package io.github.stslex.workeeper.feature.all_trainings.domain

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.all_trainings.di.TRAINING_SCOPE_NAME
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class AllTrainingsInteractorImpl(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appDispatcher: AppDispatcher
) : AllTrainingsInteractor {

    override suspend fun deleteAll(trainingsUuids: List<String>) {
        withContext(appDispatcher.default) {
            exerciseRepository.deleteByTrainingsUuids(trainingsUuids)
            trainingRepository.removeAll(trainingsUuids)
        }
    }
}