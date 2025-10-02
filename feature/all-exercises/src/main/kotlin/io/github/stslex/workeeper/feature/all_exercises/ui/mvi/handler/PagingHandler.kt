package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.toUi
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class PagingHandler @Inject constructor(
    private val repository: ExerciseRepository,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    store: ExerciseHandlerStore,
) : Handler<Action.Paging>, ExerciseHandlerStore by store {

    val processor: PagingUiState<PagingData<ExerciseUiModel>> = PagingUiState {
        state.map {
            it.query
        }.flatMapLatest { query ->
            val result = repository.getExercises(query).map { pagingData ->
                pagingData.map { it.toUi() }
            }
            println("result: $result")
            result
        }
            .flowOn(defaultDispatcher)
    }

    override fun invoke(action: Action.Paging) = Unit
}
