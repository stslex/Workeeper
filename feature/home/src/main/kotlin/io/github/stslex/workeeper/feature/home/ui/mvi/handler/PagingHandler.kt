package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.model.toUi
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(HomeScope::class)
@Scoped
class PagingHandler(
    private val repository: ExerciseRepository,
    private val appDispatcher: AppDispatcher
) : Handler<HomeStore.Action.Paging, HomeHandlerStore> {

    val processor: PagingUiState<PagingData<ExerciseUiModel>> = PagingUiState {
        repository.exercises.map { pagingData ->
            pagingData.map { it.toUi() }
        }
            .flowOn(appDispatcher.io)
    }

    override fun HomeHandlerStore.invoke(action: HomeStore.Action.Paging) {
//        TODO("Not yet implemented")
    }
}