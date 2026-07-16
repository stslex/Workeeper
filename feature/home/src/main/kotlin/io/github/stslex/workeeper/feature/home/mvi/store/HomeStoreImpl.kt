// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStoreImpl
import io.github.stslex.workeeper.feature.home.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.home.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.home.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State

// Metro constructs this PLAIN Store (class-level @Inject). Retention is
// owned by the Android ViewModelStore via rememberMetroStoreProcessor — so NO @SingleIn here.
@Inject
internal class HomeStoreImpl(
    navigationHandler: NavigationHandler,
    clickHandler: ClickHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: HomeHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.INITIAL,
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
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "Home"
    }
}
