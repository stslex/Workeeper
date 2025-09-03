package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.navigation.Config.Exercise.Data
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

internal class ExerciseComponentImpl(
    private val router: Router,
    override val data: Data
) : ExerciseComponent, ComponentContext by router {

    override fun ExerciseHandlerStore.invoke(
        action: Action.Navigation
    ) {
        when (action) {
            Action.Navigation.Back -> router.popBack()
            Action.Navigation.BackWithConfirmation -> navigationBackWithConfirmation()
        }
    }

    private fun ExerciseHandlerStore.navigationBackWithConfirmation() {
        val currentHash = state.value.calculateEqualsHash()
        val initialHash = state.value.initialHash
        val canBack = currentHash == initialHash
        if (canBack) {
            consume(Action.Navigation.Back)
        } else {
            sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DISMISS))
        }
    }
}
