package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class NavigationHandler @Inject constructor(
    store: ExerciseHandlerStore,
) : Handler<Action.NavigationMiddleware>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.NavigationMiddleware) {
        when (action) {
            Action.NavigationMiddleware.Back -> consume(Action.Navigation.Back)
            Action.NavigationMiddleware.BackWithConfirmation -> navigationBackWithConfirmation()
        }
    }

    private fun navigationBackWithConfirmation() {
        if (state.value.allowBack) {
            consume(Action.Navigation.Back)
        } else {
            sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DISMISS))
        }
    }
}
