package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

internal interface TrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingUiState: PagingUiState<PagingData<TrainingUiModel>>,
        val query: String,
        val selectedItems: ImmutableSet<String>
    ) : Store.State {

        companion object {

            fun init(
                pagingUiState: PagingUiState<PagingData<TrainingUiModel>>
            ): State = State(
                pagingUiState = pagingUiState,
                query = "",
                selectedItems = persistentSetOf(),
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Paging : Action

        sealed interface Click : Action {

            data class TrainingItemClick(
                val itemUuid: String
            ) : Click

            data class TrainingItemLongClick(
                val itemUuid: String
            ) : Click

            data object ActionButton : Click
        }

        sealed interface Navigation : Action {

            data class OpenTraining(val uuid: String) : Navigation

            data object CreateTraining : Navigation
        }
    }

    sealed interface Event : Store.Event {

        data object Haptic : Event
    }
}