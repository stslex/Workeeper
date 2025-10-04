package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class AllTrainingsInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
) : AllTrainingsInteractor {

    override suspend fun deleteAll(trainingsUuids: List<String>) {
        withContext(defaultDispatcher) {
            exerciseRepository.deleteByTrainingsUuids(trainingsUuids)
            trainingRepository.removeAll(trainingsUuids)
        }
    }

    override fun getTrainings(
        query: String,
    ): Flow<PagingData<TrainingDataModel>> = trainingRepository
        .getTrainingsUnique(query)
        .flowOn(defaultDispatcher)
}
