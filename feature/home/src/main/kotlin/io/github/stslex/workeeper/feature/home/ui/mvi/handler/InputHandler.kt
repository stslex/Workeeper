package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import io.github.stslex.workeeper.core.store.store.CommonStore
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
class InputHandler(
    private val commonStore: CommonStore
) : Handler<HomeStore.Action.Input, HomeHandlerStore> {

    override fun HomeHandlerStore.invoke(action: HomeStore.Action.Input) {
        when (action) {
            is HomeStore.Action.Input.SearchQuery -> processQueryChange(action)
            is HomeStore.Action.Input.ChangeStartDate -> processStartDateChange(action)
            is HomeStore.Action.Input.ChangeEndDate -> processEndDateChange(action)
        }
    }

    private fun HomeHandlerStore.processQueryChange(action: HomeStore.Action.Input.SearchQuery) {
        updateState { it.copyAll(query = action.query) }
    }

    private fun HomeHandlerStore.processStartDateChange(action: HomeStore.Action.Input.ChangeStartDate) {
        launch {
            commonStore.setHomeSelectedStartDate(action.timestamp)
        }
    }

    private fun HomeHandlerStore.processEndDateChange(action: HomeStore.Action.Input.ChangeEndDate) {
        launch {
            commonStore.setHomeSelectedEndDate(action.timestamp)
        }
    }
}