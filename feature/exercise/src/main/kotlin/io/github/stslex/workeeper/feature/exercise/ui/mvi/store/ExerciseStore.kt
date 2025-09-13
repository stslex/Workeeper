package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.Property
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

interface ExerciseStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val name: Property,
        val sets: ImmutableList<SetsProperty>,
        val dateProperty: DateProperty,
        val isCalendarOpen: Boolean,
        val isMenuOpen: Boolean,
        val menuItems: ImmutableSet<ExerciseUiModel>,
        val initialHash: Int,
    ) : Store.State {

        val calculateEqualsHash: Int
            get() = uuid.hashCode() +
                    name.value.trim().hashCode() +
                    sets.hashCode() +
                    dateProperty.converted.hashCode()

        val allowBack: Boolean
            get() = if (uuid == null) {
                name.value.isBlank() && sets.isEmpty()
            } else {
                calculateEqualsHash == initialHash
            }

        companion object {

            val INITIAL = State(
                uuid = null,
                name = Property.new(PropertyType.NAME),
                sets = persistentListOf(),
                dateProperty = DateProperty(0L, ""),
                isCalendarOpen = false,
                isMenuOpen = false,
                menuItems = persistentSetOf(),
                initialHash = 0
            )

            fun createInitial(data: Data?): State = INITIAL
//                data?.mapToState() ?: INITIAL.copy(
//                dateProperty = DateProperty.new(System.currentTimeMillis()),
//                initialHash = INITIAL.calculateEqualsHash
//            )
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

        sealed interface Common : Action {

            data object SearchTitle : Common

        }

        sealed interface Click : Action {

            data object Save : Click

            data object Cancel : Click

            data object Delete : Click

            data object ConfirmedDelete : Click

            data object PickDate : Click

            data object CloseCalendar : Click

            data object OpenMenuVariants : Click

            data object CloseMenuVariants : Click

            data class OnMenuItemClick(val item: ExerciseUiModel) : Click

        }

        sealed interface NavigationMiddleware : Action {

            data object BackWithConfirmation : NavigationMiddleware

            data object Back : NavigationMiddleware
        }

        sealed interface Navigation : Action {

            data object Back : Navigation
        }

    }

    sealed interface Event : Store.Event {

        data object InvalidParams : Event

        data class Snackbar(
            val type: SnackbarType
        ) : Event

        data object HapticClick : Event

    }
}

//private fun Data.mapToState(): State {
//    val state = State.INITIAL.copy(
//        uuid = uuid,
//        name = State.INITIAL.name.update(name),
//        sets = State.INITIAL.sets.update(sets.toString()),
//        reps = State.INITIAL.reps.update(reps.toString()),
//        weight = State.INITIAL.weight.update(weight.toString()),
//        dateProperty = DateProperty.new(timestamp),
//        initialHash = 0
//    )
//    return state.copy(
//        initialHash = state.calculateEqualsHash
//    )
//}
