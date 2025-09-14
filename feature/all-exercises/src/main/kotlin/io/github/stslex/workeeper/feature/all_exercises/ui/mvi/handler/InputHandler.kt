package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [InputHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class InputHandler(
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore,
) : Handler<Action.Input>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.SearchQuery -> processQueryChange(action)
        }
    }

    private fun processQueryChange(action: Action.Input.SearchQuery) {
        updateState { it.copy(query = action.query) }
    }
}