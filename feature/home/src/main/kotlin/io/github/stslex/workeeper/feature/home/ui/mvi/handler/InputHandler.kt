package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(HomeScope::class)
@Scoped
class InputHandler : Handler<HomeStore.Action.Input, HomeHandlerStore> {

    override fun HomeHandlerStore.invoke(action: HomeStore.Action.Input) {
        when (action) {
            is HomeStore.Action.Input.SearchQuery -> processQueryChange(action)
        }
    }

    private fun HomeHandlerStore.processQueryChange(action: HomeStore.Action.Input.SearchQuery) {
        updateState { it.copy(query = action.query) }
    }
}