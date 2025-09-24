package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiMapper
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [PagingHandler::class])
@Scope(name = TRAINING_SCOPE_NAME)
internal class PagingHandler(
    private val repository: TrainingRepository,
    private val trainingMapper: TrainingUiMapper,
    @param:DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    @Named(TRAINING_SCOPE_NAME) private val store: TrainingHandlerStore,
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
