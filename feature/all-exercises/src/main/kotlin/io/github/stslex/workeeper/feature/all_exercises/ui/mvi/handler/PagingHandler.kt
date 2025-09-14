package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.toUi
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [PagingHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class PagingHandler(
    private val repository: ExerciseRepository,
    private val dispatcher: AppDispatcher,
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore,
) : Handler<Action.Paging>, ExerciseHandlerStore by store {

    private val queryState = MutableStateFlow("")

    val processor: PagingUiState<PagingData<ExerciseUiModel>> = PagingUiState {
        queryState.flatMapLatest { query ->
            repository.getExercises(query).map { pagingData ->
                pagingData.map { it.toUi() }
            }
        }
            .flowOn(dispatcher.io)
    }

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> processInit()
        }
    }

    private fun processInit() {
        state.map { it.query }.launch { queryState.value = it }
    }
}
