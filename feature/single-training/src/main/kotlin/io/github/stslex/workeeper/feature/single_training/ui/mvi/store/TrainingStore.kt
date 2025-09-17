package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.persistentListOf

internal interface TrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val training: TrainingUiModel,
        val dialogState: DialogState
    ) : Store.State {

        companion object {

            val INITIAL = State(
                training = TrainingUiModel(
                    uuid = "",
                    name = "",
                    labels = persistentListOf(),
                    exerciseUuids = persistentListOf(),
                    date = DateProperty.new(System.currentTimeMillis())
                ),
                dialogState = DialogState.Closed
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data class Init(val uuid: String?) : Common
        }

        sealed interface Input : Action {

            data class Name(
                val value: String
            ) : Input

            data class Date(
                val timestamp: Long
            ) : Input
        }

        sealed interface Click : Action {

            data object Close : Click

            data object Save : Click

            data object Delete : Click

            data object OpenCalendarPicker : Click

            data object CloseCalendarPicker : Click
        }

        sealed interface Navigation : Action {

            data object PopBack : Navigation
        }
    }

    sealed interface Event : Store.Event
}