// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStoreImpl
import io.github.stslex.workeeper.feature.past_session.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State

// Metro assisted Store: @AssistedInject constructs it with the Screen.PastSession route arg via
// @Assisted; the graph exposes the @AssistedFactory (never the Store). No Hilt @HiltViewModel.
// Retention is owned by the Android ViewModelStore via rememberMetroStoreProcessor — no @SingleIn.
@AssistedInject
internal class PastSessionStoreImpl(
    @Assisted screen: Screen.PastSession,
    navigationHandler: NavigationHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: PastSessionHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(sessionUuid = screen.sessionUuid),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<Screen.PastSession, PastSessionStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "PastSession"
    }
}
