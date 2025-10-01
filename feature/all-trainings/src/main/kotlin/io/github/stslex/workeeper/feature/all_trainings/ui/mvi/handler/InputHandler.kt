package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped([InputHandler::class])
@Scope(name = TRAINING_SCOPE_NAME)
internal class InputHandler(
    @Named(TRAINING_SCOPE_NAME) store: TrainingHandlerStore,
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
