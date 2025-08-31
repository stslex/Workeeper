package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(HomeScope::class)
@Scoped
class ClickHandler : Handler<Action.Click, HomeHandlerStore> {

    override fun HomeHandlerStore.invoke(action: Action.Click) {
        when (action) {
            Action.Click.ButtonAddClick -> processClickAdd()
        }
    }

    private fun HomeHandlerStore.processClickAdd() {

    }
}