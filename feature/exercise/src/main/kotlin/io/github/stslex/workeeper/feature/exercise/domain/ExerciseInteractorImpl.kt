package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.exercise.training.toChangeModel
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped


@Scope(name = EXERCISE_SCOPE_NAME)
@Scoped
internal class ExerciseInteractorImpl(
    private val exerciseRepository: ExerciseRepository,
    private val trainingRepository: TrainingRepository,
    @param:DefaultDispatcher
    private val dispatcher: CoroutineDispatcher
) : ExerciseInteractor {

    override suspend fun saveItem(item: ExerciseChangeDataModel) {
        withContext(dispatcher) {
            exerciseRepository.saveItem(item)
            val updatedItem = exerciseRepository.getExerciseByName(item.name)

            updatedItem
                ?.trainingUuid
                ?.let { trainingRepository.getTraining(it) }
                ?.let { training ->
                    if (training.exerciseUuids.contains(updatedItem.uuid).not()) {
                        trainingRepository.updateTraining(
                            training = training.copy(
                                exerciseUuids = training.exerciseUuids + updatedItem.uuid
                            ).toChangeModel()
                        )
                    }
                }
        }
    }

    override suspend fun deleteItem(uuid: String) {
        withContext(dispatcher) {
            exerciseRepository.deleteItem(uuid)
        }
    }

    override suspend fun getExercise(uuid: String): ExerciseDataModel? = withContext(dispatcher) {
        exerciseRepository.getExercise(uuid)
    }

    override suspend fun searchItems(query: String): List<ExerciseDataModel> =
        withContext(dispatcher) {
            exerciseRepository.searchItems(query)
        }
}