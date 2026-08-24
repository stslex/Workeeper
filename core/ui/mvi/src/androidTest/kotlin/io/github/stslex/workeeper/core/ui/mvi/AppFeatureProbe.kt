// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.Action
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.Event
import io.github.stslex.workeeper.core.ui.mvi.AppRootProbeStore.State
import io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.di.StoreGenerationDeps
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
 * The one app-scope binding the production Store path reads: `rememberStoreProcessor` resolves
 * `appDeps<StoreGenerationDeps>()` to parent every Store job to the current generation.
 */
internal class ProbeGenerationDeps(
    override val appScopeLifetime: AppScopeLifetime = AppScopeLifetime(),
) : StoreGenerationDeps

/**
 * Supplies [ProbeGenerationDeps] the way production does — through an `applicationContext` that
 * implements [AppDepsHolder] — without giving this module an app graph.
 */
@Composable
internal fun ProbeAppDepsHost(
    deps: ProbeGenerationDeps,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val wrapped = remember(base, deps) {
        val holder = object : ContextWrapper(base.applicationContext), AppDepsHolder {
            override fun appDeps(): Any = deps
        }
        object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = holder
        }
    }
    CompositionLocalProvider(LocalContext provides wrapped, content = content)
}

/**
 * Resolves its Store through the same Metro path production `AppFeature`s use; only the generation
 * lifetime arrives through the real `appDeps` seam, because that seam is what is under test.
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
