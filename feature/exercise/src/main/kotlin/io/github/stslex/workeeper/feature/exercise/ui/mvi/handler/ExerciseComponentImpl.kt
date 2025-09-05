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
        if (state.value.allowBack) {
            consume(Action.Navigation.Back)
        } else {
            sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DISMISS))
        }
    }
}
