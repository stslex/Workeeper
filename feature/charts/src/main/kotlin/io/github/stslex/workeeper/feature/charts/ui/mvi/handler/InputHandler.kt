package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [InputHandler::class])
@Scope(name = CHARTS_SCOPE_NAME)
internal class InputHandler(
    private val commonStore: CommonDataStore,
    @Named(CHARTS_SCOPE_NAME) store: ChartsHandlerStore,
) : Handler<Action.Input>, ChartsHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.ChangeStartDate -> processStartDateChange(action)
            is Action.Input.ChangeEndDate -> processEndDateChange(action)
        }
    }

    private fun processStartDateChange(action: Action.Input.ChangeStartDate) {
        launch {
            commonStore.setHomeSelectedStartDate(action.timestamp)
        }
    }

    private fun processEndDateChange(action: Action.Input.ChangeEndDate) {
        launch {
            commonStore.setHomeSelectedEndDate(action.timestamp)
        }
    }
}
