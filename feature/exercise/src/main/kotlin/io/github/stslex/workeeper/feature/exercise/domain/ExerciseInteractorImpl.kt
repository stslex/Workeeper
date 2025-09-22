package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.exercise.training.toChangeModel
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped


@Scope(name = EXERCISE_SCOPE_NAME)
@Scoped
internal class ExerciseInteractorImpl(
    private val exerciseRepository: ExerciseRepository,
    private val trainingRepository: TrainingRepository
) : ExerciseInteractor {

    override suspend fun saveItem(item: ExerciseChangeDataModel) {
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