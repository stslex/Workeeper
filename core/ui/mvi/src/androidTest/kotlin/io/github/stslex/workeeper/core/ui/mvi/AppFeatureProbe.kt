// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
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

internal class AppRootProbeStartHandler(
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

/**
 * App-Scope Collapse Step 6 (Phase 3.4): de-Hilt'd. The former `@HiltViewModel` / `@Inject` /
 * `@ViewModelScoped` probe classes are now plain classes, and the Store is resolved through the same
 * Metro path every production `AppFeature` uses (`rememberMetroStoreProcessor`, see `AppDialogFeature`) —
 * the deps (`StoreDispatchers`, `AnalyticsHolder`, `LoggerHolder`) are constructed directly with reals,
 * no app graph. This preserves the mount-site scope invariant [AppFeatureScopeTest] asserts:
 * `rememberMetroStoreProcessor` retains the Store in the current `LocalViewModelStoreOwner`.
 */
internal object AppRootProbeFeature : AppFeature<AppRootProbeStoreProcessor>() {

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
            )
        } as AppRootProbeStoreProcessor
}
