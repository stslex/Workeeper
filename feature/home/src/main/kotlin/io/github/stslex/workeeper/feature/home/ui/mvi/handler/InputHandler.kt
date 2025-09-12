package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import io.github.stslex.workeeper.core.store.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HOME_SCOPE_NAME
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [InputHandler::class])
@Scope(name = HOME_SCOPE_NAME)
internal class InputHandler(
    private val commonStore: CommonDataStore,
    @Named(HOME_SCOPE_NAME) store: HomeHandlerStore,
) : Handler<HomeStore.Action.Input>, HomeHandlerStore by store {

    override fun invoke(action: HomeStore.Action.Input) {
        when (action) {
            is HomeStore.Action.Input.SearchQuery -> processQueryChange(action)
            is HomeStore.Action.Input.ChangeStartDate -> processStartDateChange(action)
            is HomeStore.Action.Input.ChangeEndDate -> processEndDateChange(action)
        }
    }

    private fun processQueryChange(action: HomeStore.Action.Input.SearchQuery) {
        updateState { it.copyAll(query = action.query) }
    }

    private fun processStartDateChange(action: HomeStore.Action.Input.ChangeStartDate) {
        launch {
            commonStore.setHomeSelectedStartDate(action.timestamp)
        }
    }

    private fun processEndDateChange(action: HomeStore.Action.Input.ChangeEndDate) {
        launch {
            commonStore.setHomeSelectedEndDate(action.timestamp)
        }
    }
}