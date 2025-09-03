package io.github.stslex.workeeper.feature.home.ui.mvi.store

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State

interface HomeStore : Store<State, Action, Event> {

    data class State(
        val items: PagingUiState<PagingData<ExerciseUiModel>>
    ) : Store.State

    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Click : Action {

            data object ButtonAddClick : Click

            data class Item(val item: ExerciseUiModel) : Click
        }

        sealed interface Navigation : Action, Store.Action.Navigation {

            data object CreateExerciseDialog : Navigation

            data class OpenExercise(val data: Data) : Navigation
        }

    }

    sealed interface Event : Store.Event

}
