package io.github.stslex.workeeper.feature.charts.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStoreImpl
import io.github.stslex.workeeper.feature.charts.mvi.handler.ChartsComponent
import io.github.stslex.workeeper.feature.charts.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.charts.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.charts.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.State

@HiltViewModel(assistedFactory = ChartsStoreImpl.Factory::class)
internal class ChartsStoreImpl @AssistedInject constructor(
    @Assisted component: ChartsComponent,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ChartsHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.INITIAL,
    storeEmitter = storeEmitter,
    storeDispatchers = storeDispatchers,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> component
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    initialActions = listOf(Action.Paging.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<ChartsComponent, ChartsStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "Charts"
    }
}
