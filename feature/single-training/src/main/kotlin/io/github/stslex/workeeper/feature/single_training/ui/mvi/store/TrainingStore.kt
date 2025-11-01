package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

internal interface TrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val training: TrainingUiModel,
        val dialogState: DialogState,
        val pendingForCreateUuid: String,
    ) : Store.State {

        fun copyTraining(
            uuid: String = training.uuid,
            name: PropertyHolder.StringProperty = training.name,
            isMenuOpen: Boolean = training.isMenuOpen,
            menuItems: ImmutableSet<MenuItem<TrainingUiModel>> = training.menuItems,
            labels: ImmutableList<String> = training.labels,
            exercises: ImmutableList<ExerciseUiModel> = training.exercises,
            date: PropertyHolder.DateProperty = training.date,
        ): State = copy(
            training = training.copy(
                uuid = uuid,
                name = name,
                isMenuOpen = isMenuOpen,
                menuItems = menuItems,
                labels = labels,
                exercises = exercises,
                date = date,
            ),
        )

        companion object {

            val INITIAL = State(
                training = TrainingUiModel.INITIAL,
                dialogState = DialogState.Closed,
                pendingForCreateUuid = "",
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data class Init(val uuid: String?) : Common
        }

        sealed interface Input : Action {

            data class Name(
                val value: String,
            ) : Input

            data class Date(
                val timestamp: Long,
            ) : Input
        }

        sealed interface Click : Action {

            data object Close : Click

            data object Save : Click

            data object DeleteDialogOpen : Click

            data object OpenCalendarPicker : Click

            data object CloseCalendarPicker : Click

            data object CreateExercise : Click

            data class ExerciseClick(val exerciseUuid: String) : Click

            sealed interface DialogDeleteTraining : Click {

                data object Confirm : DialogDeleteTraining

                data object Dismiss : DialogDeleteTraining
            }

            sealed interface Menu : Click {

                data object Open : Menu

                data object Close : Menu

                data class Item(val item: MenuItem<TrainingUiModel>) : Menu
            }
        }

        sealed interface Navigation : Action {

            data object PopBack : Navigation

            data class CreateExercise(
                val trainingUuid: String,
            ) : Navigation

            data class OpenExercise(
                val exerciseUuid: String,
                val trainingUuid: String,
            ) : Navigation
        }
    }

    sealed interface Event : Store.Event {

        data class Haptic(val hapticFeedbackType: HapticFeedbackType) : Event
    }
}
