package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Config.Exercise.Data
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.Property
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

interface ExerciseStore : Store<State, Action, Event> {

    data class State(
        val uuid: String? = null,
        val name: Property = Property.new(PropertyType.NAME),
        val sets: Property = Property.new(PropertyType.SETS),
        val reps: Property = Property.new(PropertyType.REPS),
        val weight: Property = Property.new(PropertyType.WEIGHT),
        val timestamp: Long = System.currentTimeMillis(),
        val isLoading: Boolean = false,
    ) : Store.State

    sealed interface Action : Store.Action {

        sealed interface Input : Action {

            data class Property(
                val type: PropertyType,
                val value: String
            ) : Input

            data class Time(val timestamp: Long) : Input
        }

        data class InitDialog(val data: Data) : Action

        sealed interface Click : Action {

            data object Save : Click

            data object Cancel : Click

            data object Delete : Click

            data object ConfirmedDelete : Click

        }

        sealed interface Navigation : Action, Store.Action.Navigation {

            data object Back : Navigation
        }

    }

    sealed interface Event : Store.Event {

        data object InvalidParams : Event

        data object SnackbarDelete : Event
    }
}
