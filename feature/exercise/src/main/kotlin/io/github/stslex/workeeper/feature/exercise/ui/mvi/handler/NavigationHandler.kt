package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [NavigationHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class NavigationHandler(
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore
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