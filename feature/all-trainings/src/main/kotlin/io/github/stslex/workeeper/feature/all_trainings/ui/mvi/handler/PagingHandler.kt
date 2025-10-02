package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiMapper
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class PagingHandler @Inject constructor(
    private val repository: TrainingRepository,
    private val trainingMapper: TrainingUiMapper,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    private val store: TrainingHandlerStore,
) : Handler<Action.Paging>, TrainingHandlerStore by store {

    val pagingUiState: PagingUiState<PagingData<TrainingUiModel>> = PagingUiState {
        state.map { it.query }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                repository.getTrainings(query)
                    .map { pagingData -> pagingData.map(trainingMapper::invoke) }
            }
            .flowOn(defaultDispatcher)
    }

    override fun invoke(action: Action.Paging) = Unit
}
