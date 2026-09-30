package io.github.stslex.workeeper.wear.mvi.store

import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.wear.di.WearHandlerStoreImpl
import io.github.stslex.workeeper.wear.mvi.handler.ClickHandler
import io.github.stslex.workeeper.wear.mvi.handler.CommonHandler
import io.github.stslex.workeeper.wear.mvi.handler.InputHandler
import io.github.stslex.workeeper.wear.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.mvi.store.WearStore.State

@Inject
internal class WearStoreImpl(
    commonHandler: CommonHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    navigationHandler: NavigationHandler,
    handlerStore: WearHandlerStoreImpl,
    storeDispatchers: StoreDispatchers,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<State, Action, Event>(
    name = "WearController",
    initialState = State(),
    handlerCreator = { action ->
        when (action) {
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.Navigation -> navigationHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
    appScopeLifetime = appScopeLifetime,
),
    WearStore
