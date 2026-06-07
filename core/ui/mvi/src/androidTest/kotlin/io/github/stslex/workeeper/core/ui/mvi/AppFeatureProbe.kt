// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.Action
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.Event
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.State
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import javax.inject.Inject

internal interface AppRootProbeStore : Store<State, Action, Event> {

    data class State(val tick: Int = 0) : Store.State

    sealed interface Action : Store.Action {
        data object Init : Action
    }

    sealed interface Event : Store.Event {
        data object InitCompleted : Event
    }
}

@ViewModelScoped
internal class AppRootProbeHandlerStore @Inject constructor() :
    BaseHandlerStore<State, Action, Event>()

@ViewModelScoped
internal class AppRootProbeStartHandler @Inject constructor(
    private val store: AppRootProbeHandlerStore,
) : Handler<Action.Init> {

    override fun invoke(action: Action.Init) {
        store.updateState { it.copy(tick = it.tick + 1) }
    }
}

@HiltViewModel
internal class AppRootProbeStoreImpl @Inject constructor(
    handler: AppRootProbeStartHandler,
    storeEmitter: AppRootProbeHandlerStore,
    storeDispatchers: StoreDispatchers,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State(),
    storeEmitter = storeEmitter,
    handlerCreator = { _ -> handler },
    storeDispatchers = storeDispatchers,
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
),
    AppRootProbeStore {

    companion object {
        private const val NAME = "AppRootProbe"
    }
}

internal typealias AppRootProbeStoreProcessor = StoreProcessor<State, Action, Event>

internal object AppRootProbeFeature : AppFeature<AppRootProbeStoreProcessor>() {

    @Composable
    override fun processor(): AppRootProbeStoreProcessor = createProcessor<AppRootProbeStoreImpl>()
}
