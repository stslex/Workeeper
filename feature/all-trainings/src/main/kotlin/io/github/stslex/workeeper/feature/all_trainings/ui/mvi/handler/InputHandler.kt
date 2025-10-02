package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: TrainingHandlerStore,
) : Handler<Action.Input>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.SearchQuery -> processSearchQuery(action)
            is Action.Input.KeyboardChange -> processKeyboardChange(action)
        }
    }

    private fun processKeyboardChange(action: Action.Input.KeyboardChange) {
        updateState { it.copy(isKeyboardVisible = action.isVisible) }
    }

    private fun processSearchQuery(action: Action.Input.SearchQuery) {
        updateState { it.copy(query = action.query) }
    }
}
