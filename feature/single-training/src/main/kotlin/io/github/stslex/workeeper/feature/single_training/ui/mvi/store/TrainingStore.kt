package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.persistentListOf

internal interface TrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val training: TrainingUiModel
    ) : Store.State {

        companion object {

            val INITIAL = State(
                training = TrainingUiModel(
                    uuid = "",
                    name = "",
                    labels = persistentListOf(),
                    exerciseUuids = persistentListOf(),
                    timestamp = DateProperty.new(System.currentTimeMillis())
                )
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data class Init(val uuid: String?) : Common
        }

        sealed interface Navigation : Action {

            data object PopBack : Navigation
        }
    }

    sealed interface Event : Store.Event
}