package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
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
        val name: PropertyHolder.StringProperty,
        val sets: ImmutableList<SetsUiModel>,
        val dateProperty: PropertyHolder.DateProperty,
        val dialogState: DialogState,
        val isMenuOpen: Boolean,
        val menuItems: ImmutableSet<MenuItem<ExerciseUiModel>>,
        val trainingUuid: String?,
        val labels: ImmutableList<String>,
        val initialHash: Int,
    ) : Store.State {

        val calculateEqualsHash: Int
            get() = uuid.hashCode() +
                    name.value.trim().hashCode() +
                    sets.sumOf { it.reps.value.hashCode() + it.weight.value.hashCode() + it.type.ordinal } +
                    dateProperty.value.hashCode()

        val allowBack: Boolean
            get() = if (uuid == null) {
                name.value.trim().isBlank() && sets.all {
                    it.weight.value == it.weight.defaultValue && it.reps.value == it.reps.defaultValue
                }
            } else {
                calculateEqualsHash == initialHash
            }

        companion object {

            val INITIAL
                get() = State(
                    uuid = null,
                    name = PropertyHolder.StringProperty(),
                    sets = persistentListOf(),
                    dateProperty = PropertyHolder.DateProperty(),
                    dialogState = DialogState.Closed,
                    isMenuOpen = false,
                    menuItems = persistentSetOf(),
                    trainingUuid = null,
                    labels = persistentListOf(),
                    initialHash = 0
                )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Input : Action {

            data class PropertyName(
                val value: String
            ) : Input

            data class Time(val timestamp: Long) : Input

            sealed interface DialogSets : Input {


                data class Weight(val value: String) : DialogSets

                data class Reps(val value: String) : DialogSets

            }
        }

        sealed interface Common : Action {

            data class Init(
                val uuid: String?,
                val trainingUuid: String?
            ) : Common

        }

        sealed interface Click : Action {

            data object Save : Click

            data object Cancel : Click

            data object Delete : Click

            data object ConfirmedDelete : Click

            data object PickDate : Click

            data object CloseDialog : Click

            data object OpenMenuVariants : Click

            data object CloseMenuVariants : Click

            data class OnMenuItemClick(val item: MenuItem<ExerciseUiModel>) : Click

            sealed interface DialogSets : Click {

                data class OpenEdit(val set: SetsUiModel) : DialogSets

                data object OpenCreate : DialogSets

                data class DismissSetsDialog(val set: SetsUiModel) : DialogSets

                data class DeleteButton(val uuid: String) : DialogSets

                data class SaveButton(val set: SetsUiModel) : DialogSets

                data object CancelButton : DialogSets

            }
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
