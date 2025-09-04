package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

internal class ExerciseComponentImpl(
    private val navigator: Navigator,
    override val data: Data?
) : ExerciseComponent {

    override fun ExerciseHandlerStore.invoke(
        action: Action.Navigation
    ) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            Action.Navigation.BackWithConfirmation -> navigationBackWithConfirmation()
        }
    }

    private fun ExerciseHandlerStore.navigationBackWithConfirmation() {
        val canBack = if (state.value.uuid == null) {
            allowBackForNewItem()
        } else {
            allowBackForEditItem()
        }
        if (canBack) {
            consume(Action.Navigation.Back)
        } else {
            sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DISMISS))
        }
    }

    private fun ExerciseHandlerStore.allowBackForNewItem(): Boolean {
        return state.value.name.value.isBlank() &&
                state.value.weight.value.isBlank() &&
                state.value.sets.value.isBlank() &&
                state.value.reps.value.isBlank()
    }

    private fun ExerciseHandlerStore.allowBackForEditItem(): Boolean {
        return state.value.calculateEqualsHash() == state.value.initialHash
    }
}
