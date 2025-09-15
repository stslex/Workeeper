package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State

internal interface TrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingUiState: PagingUiState<PagingData<TrainingUiModel>>,
        val query: String,
    ) : Store.State {

        companion object {

            fun init(
                pagingUiState: PagingUiState<PagingData<TrainingUiModel>>
            ): State = State(
                pagingUiState = pagingUiState,
                query = "",
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Paging : Action

        sealed interface Navigation : Action
    }

    sealed interface Event : Store.Event
}