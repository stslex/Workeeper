// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStoreImpl
import io.github.stslex.workeeper.feature.home.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.home.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.home.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.home.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State

// Metro constructs this PLAIN Store (class-level @Inject). Retention is
// owned by the Android ViewModelStore via rememberMetroStoreProcessor — so NO @SingleIn here.
@Inject
class HomeStoreImpl internal constructor(
    navigationHandler: NavigationHandler,
    clickHandler: ClickHandler,
    commonHandler: CommonHandler,
    pagingHandler: PagingHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: HomeHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(pagingUiState = pagingHandler.pagingUiState),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
    appScopeLifetime = appScopeLifetime,
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "Home"
    }
}
