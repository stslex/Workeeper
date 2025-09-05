package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.Property
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

interface ExerciseStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val name: Property,
        val sets: Property,
        val reps: Property,
        val weight: Property,
        val timestamp: Long,
        val initialHash: Int,
    ) : Store.State {

        val calculateEqualsHash: Int
            get() = uuid.hashCode() +
                    name.value.trim().hashCode() +
                    sets.value.trim().hashCode() +
                    reps.value.trim().hashCode() +
                    weight.value.trim().hashCode() +
                    timestamp.hashCode()

        val allowBack: Boolean
            get() = if (uuid == null) {
                name.value.isBlank() &&
                        weight.value.isBlank() &&
                        sets.value.isBlank() &&
                        reps.value.isBlank()
            } else {
                calculateEqualsHash == initialHash
            }

        companion object {

            val INITIAL = State(
                uuid = null,
                name = Property.new(PropertyType.NAME),
                sets = Property.new(PropertyType.SETS),
                reps = Property.new(PropertyType.REPS),
                weight = Property.new(PropertyType.WEIGHT),
                timestamp = 0L,
                initialHash = 0
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Input : Action {

            data class Property(
                val type: PropertyType,
                val value: String
            ) : Input

            data class Time(val timestamp: Long) : Input
        }

        sealed interface Click : Action {

            data object Save : Click

            data object Cancel : Click

            data object Delete : Click

            data object ConfirmedDelete : Click

        }

        sealed interface Navigation : Action, Store.Action.Navigation {

            data object BackWithConfirmation : Navigation

            data object Back : Navigation
        }

    }

    sealed interface Event : Store.Event {

        data object InvalidParams : Event

        data class Snackbar(
            val type: SnackbarType
        ) : Event

    }
}
