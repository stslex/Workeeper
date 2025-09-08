package io.github.stslex.workeeper.feature.home.ui.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

interface HomeStore : Store<State, Action, Event> {

    data class State(
        val items: PagingUiState<PagingData<ExerciseUiModel>>,
        val selectedItems: ImmutableSet<ExerciseUiModel> = persistentSetOf(),
        val query: String = "",
    ) : Store.State

    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Input : Action {

            data class SearchQuery(val query: String) : Input
        }

        sealed interface Click : Action {

            data object FloatButtonClick : Click

            data class Item(val item: ExerciseUiModel) : Click

            data class LonkClick(val item: ExerciseUiModel) : Click
        }

        sealed interface Navigation : Action, Store.Action.Navigation {

            data object CreateExerciseDialog : Navigation

            data class OpenExercise(val data: Data) : Navigation
        }

    }

    sealed interface Event : Store.Event {

        data class HapticFeedback(
            val type: HapticFeedbackType
        ) : Event
    }

}
