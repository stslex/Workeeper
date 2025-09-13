package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.data.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.toUi
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.collections.immutable.toPersistentSet
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [CommonHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class CommonHandler(
    private val repository: ExerciseRepository,
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore
) : Handler<Action.Common>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.SearchTitle -> processTitleSearch()
        }
    }

    private fun processTitleSearch() {
        launch(
            onSuccess = { result ->
                updateState { state ->
                    state.copy(
                        menuItems = result.map { item -> item.toUi() }.toPersistentSet()
                    )
                }
            }
        ) {
            repository.searchItems(state.value.name.value)
        }
    }
}