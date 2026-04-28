// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.store

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
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStoreImpl
import io.github.stslex.workeeper.feature.past_session.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.past_session.mvi.handler.PastSessionComponent
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State

@HiltViewModel(assistedFactory = PastSessionStoreImpl.Factory::class)
internal class PastSessionStoreImpl @AssistedInject constructor(
    @Assisted component: PastSessionComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: PastSessionHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(sessionUuid = component.data.sessionUuid),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component as NavigationHandler
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
    interface Factory : StoreFactory<PastSessionComponent, PastSessionStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "PastSession"
    }
}
