// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.Action
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.Event
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.State
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import kotlinx.coroutines.Dispatchers

internal interface AppRootProbeStore : Store<State, Action, Event> {

    data class State(val tick: Int = 0) : Store.State

    sealed interface Action : Store.Action {
        data object Init : Action
    }

    sealed interface Event : Store.Event {
        data object InitCompleted : Event
    }
}

internal class AppRootProbeHandlerStore : BaseHandlerStore<State, Action, Event>()

internal abstract class AppRootProbeScope private constructor()

@SingleIn(AppRootProbeScope::class)
internal class AppRootProbeStartHandler @Inject constructor(
    private val store: AppRootProbeHandlerStore,
) : Handler<Action.Init> {

    override fun invoke(action: Action.Init) {
        store.updateState { it.copy(tick = it.tick + 1) }
    }
}

internal class AppRootProbeStoreImpl(
    handler: AppRootProbeStartHandler,
    storeEmitter: AppRootProbeHandlerStore,
    storeDispatchers: StoreDispatchers,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State(),
    storeEmitter = storeEmitter,
    handlerCreator = { _ -> handler },
    storeDispatchers = storeDispatchers,
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
    appScopeLifetime = appScopeLifetime,
),
    AppRootProbeStore {

    companion object {
        private const val NAME = "AppRootProbe"
    }
}

internal typealias AppRootProbeStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves its Store through the same Metro path production `AppFeature`s use. The generation
 * lifetime is a plain constructor argument, exactly as it is for the 13 production Stores.
 */
internal class AppRootProbeFeature(
    private val appScopeLifetime: AppScopeLifetime,
) : AppFeature<AppRootProbeStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppRootProbeStoreProcessor =
        rememberMetroStoreProcessor<AppRootProbeStoreImpl> {
            val handlerStore = AppRootProbeHandlerStore()
            AppRootProbeStoreImpl(
                handler = AppRootProbeStartHandler(handlerStore),
                storeEmitter = handlerStore,
                storeDispatchers = StoreDispatchers(
                    defaultDispatcher = Dispatchers.Default,
                    mainImmediateDispatcher = Dispatchers.Main.immediate,
                ),
                analyticsHolder = AnalyticsHolder(),
                loggerHolder = LoggerHolder(),
                appScopeLifetime = appScopeLifetime,
            )
        } as AppRootProbeStoreProcessor
}
