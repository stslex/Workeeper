package io.github.stslex.workeeper.feature.charts.ui.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStoreImpl
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.ChartsComponent
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State

@HiltViewModel(assistedFactory = ChartsStoreImpl.Factory::class)
internal class ChartsStoreImpl @AssistedInject constructor(
    @Assisted navigationHandler: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ChartsHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME),
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.INITIAL,
    storeEmitter = storeEmitter,
    storeDispatchers = storeDispatchers,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> navigationHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    initialActions = listOf(Action.Paging.Init),
    analytics = analytics,
    logger = logger,
) {

    @AssistedFactory
    interface Factory {
        fun create(component: ChartsComponent): ChartsStoreImpl
    }

    companion object {

        @VisibleForTesting
        private const val NAME = "Charts"
    }
}
