package io.github.stslex.workeeper.feature.exercise.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.exercise.training.toChangeModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

@ViewModelScoped
internal class ExerciseInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val trainingRepository: TrainingRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ExerciseInteractor {

    override suspend fun saveItem(item: ExerciseChangeDataModel) {
        withContext(dispatcher) {
            val createdUuid = item.uuid.orEmpty().ifBlank {
                Uuid.random().toString()
            }
            val createdItem = item.copy(
                uuid = createdUuid,
            )
            exerciseRepository.saveItem(createdItem)
            val updatedItem = exerciseRepository.getExercise(createdUuid)

            updatedItem
                ?.trainingUuid
                ?.let { trainingRepository.getTraining(it) }
                ?.let { training ->
                    if (training.exerciseUuids.contains(updatedItem.uuid).not()) {
                        trainingRepository.updateTraining(
                            training = training.copy(
                                exerciseUuids = training.exerciseUuids + updatedItem.uuid,
                            ).toChangeModel(),
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
