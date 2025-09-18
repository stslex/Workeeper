package io.github.stslex.workeeper.feature.single_training.domain.interactor

import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import io.github.stslex.workeeper.feature.single_training.domain.model.toDomain
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class SingleTrainingInteractorImpl(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appDispatcher: AppDispatcher
) : SingleTrainingInteractor {

    override suspend fun getTraining(
        uuid: String
    ): TrainingDomainModel? = withContext(appDispatcher.default) {
        trainingRepository.getTraining(uuid).let { training ->
            training?.toDomain(
                exercises = training.exerciseUuids
                    .asyncMap { exerciseUuid ->
                        exerciseRepository.getExercise(exerciseUuid)?.toDomain()
                    }
                    .filterNotNull()
            )
        }
    }

    override suspend fun removeTraining(uuid: String) {
        withContext(appDispatcher.default) {
            launch {
                trainingRepository.removeTraining(uuid)
            }
            launch {
                exerciseRepository.deleteByTrainingUuid(uuid)
            }
        }
    }

    override suspend fun updateTraining(training: TrainingDomainChangeModel) {
        withContext(appDispatcher.default) {
            training.uuid?.let { uuid ->
                trainingRepository.getTraining(uuid)?.exerciseUuids.orEmpty()
                    .filter { uuid -> training.exercisesUuids.contains(uuid).not() }
                    .asyncMap { exerciseUuid -> exerciseRepository.deleteItem(exerciseUuid) }
            }
            trainingRepository.updateTraining(training.toData())
        }
    }
}