package io.github.stslex.workeeper.feature.single_training.domain.interactor

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import io.github.stslex.workeeper.feature.single_training.domain.model.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class SingleTrainingInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
) : SingleTrainingInteractor {

    override suspend fun getTraining(
        uuid: String,
    ): TrainingDomainModel? = withContext(defaultDispatcher) {
        trainingRepository.getTraining(uuid).let { training ->
            training?.toDomain(
                exercises = training.exerciseUuids
                    .asyncMap { exerciseUuid ->
                        exerciseRepository.getExercise(exerciseUuid)?.toDomain()
                    }
                    .filterNotNull(),
            )
        }
    }

    override fun subscribeForTraining(
        uuid: String,
    ): Flow<TrainingDomainModel> = trainingRepository
        .subscribeForTraining(uuid)
        .map { training ->
            training.toDomain(
                exercises = training.exerciseUuids
                    .asyncMap { exerciseUuid ->
                        exerciseRepository.getExercise(exerciseUuid)?.toDomain()
                    }
                    .filterNotNull(),
            )
        }
        .flowOn(defaultDispatcher)

    override suspend fun removeTraining(uuid: String) {
        withContext(defaultDispatcher) {
            launch {
                trainingRepository.removeTraining(uuid)
            }
            launch {
                exerciseRepository.deleteByTrainingUuid(uuid)
            }
        }
    }

    override suspend fun updateTraining(training: TrainingDomainChangeModel) {
        withContext(defaultDispatcher) {
            training.uuid?.let { uuid ->
                trainingRepository.getTraining(uuid)?.exerciseUuids.orEmpty()
                    .filter { uuid -> training.exercisesUuids.contains(uuid).not() }
                    .asyncMap { exerciseUuid -> exerciseRepository.deleteItem(exerciseUuid) }
            }
            trainingRepository.updateTraining(training.toData())
        }
    }

    override suspend fun searchTrainings(
        query: String,
    ): List<TrainingDomainModel> = withContext(defaultDispatcher) {
        trainingRepository
            .searchTrainingsUnique(
                query = query,
                limit = 10, // limit to 10 results to avoid UI jank on large data sets
            )
            .asyncMap { training ->
                training
                    .toDomain(
                        exercises = training.exerciseUuids
                            .asyncMap { uuid ->
                                exerciseRepository.getExercise(uuid)?.toDomain()
                            }
                            .filterNotNull(),
                    )
            }
    }
}
