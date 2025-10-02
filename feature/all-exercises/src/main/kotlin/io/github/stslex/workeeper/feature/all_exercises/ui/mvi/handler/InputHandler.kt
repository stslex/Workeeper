package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: ExerciseHandlerStore,
) : Handler<Action.Input>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.SearchQuery -> processQueryChange(action)
            is Action.Input.KeyboardChange -> processKeyboardChange(action)
        }
    }

    private fun processKeyboardChange(action: Action.Input.KeyboardChange) {
        updateState { it.copy(isKeyboardVisible = action.isVisible) }
    }

    private fun processQueryChange(action: Action.Input.SearchQuery) {
        updateState { it.copy(query = action.query) }
    }
}
