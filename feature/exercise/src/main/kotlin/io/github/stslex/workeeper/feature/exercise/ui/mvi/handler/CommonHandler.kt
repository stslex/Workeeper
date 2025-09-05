package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.toUi
import kotlinx.collections.immutable.toPersistentSet
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(ExerciseScope::class)
@Scoped
class CommonHandler(
    private val repository: ExerciseRepository
) : Handler<ExerciseStore.Action.Common, ExerciseHandlerStore> {

    override fun ExerciseHandlerStore.invoke(action: ExerciseStore.Action.Common) {
        when (action) {
            ExerciseStore.Action.Common.SearchTitle -> processTitleSearch()
        }
    }

    private fun ExerciseHandlerStore.processTitleSearch() {
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