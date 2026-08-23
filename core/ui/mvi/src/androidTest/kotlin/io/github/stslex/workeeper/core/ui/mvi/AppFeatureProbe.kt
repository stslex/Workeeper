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
 * The ONE app-scope binding the production Store path reads (Phase 5 R3, spec §8.4):
 * `rememberStoreProcessor` resolves `appDeps<StoreGenerationDeps>()` to parent every Store job to
 * the current generation. The probe owns its own [AppScopeLifetime] so the test can end it and
 * observe the parenting directly.
 */
internal class ProbeGenerationDeps(
    override val appScopeLifetime: AppScopeLifetime = AppScopeLifetime(),
) : StoreGenerationDeps

/**
 * Supplies [ProbeGenerationDeps] the way production does — through an `applicationContext` that
 * implements [AppDepsHolder] — WITHOUT giving this module an app graph.
 *
 * The seam is deliberately strict: `appDeps` casts the application context and throws when it is
 * not a holder, which is what forbids a Store from silently starting un-parented jobs. So the
 * repair here is to satisfy the contract, never to soften it — a `?: null` fallback in
 * `rememberStoreProcessor` would compile, keep this probe green, and re-open exactly the defect
 * (Store jobs outliving the database they touch) that the required `generationJob` parameter
 * exists to make impossible. Overriding [LocalContext] leaves `LocalViewModelStoreOwner` and
 * `LocalLifecycleOwner` untouched, so the mount-site scope invariant under test is unaffected.
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
 * App-Scope Collapse Step 6 (Phase 3.4): de-Hilt'd. The former `@HiltViewModel` / `@Inject` /
 * `@ViewModelScoped` probe classes are now plain classes, and the Store is resolved through the same
 * Metro path every production `AppFeature` uses (`rememberMetroStoreProcessor`, see `AppDialogFeature`) —
 * the deps (`StoreDispatchers`, `AnalyticsHolder`, `LoggerHolder`) are constructed directly with reals.
 * The one thing it does NOT construct directly is the generation lifetime: that arrives through the real
 * `appDeps` seam via [ProbeAppDepsHost], because the seam itself is under test. This preserves the
 * mount-site scope invariant [AppFeatureScopeTest] asserts: `rememberMetroStoreProcessor` retains the
 * Store in the current `LocalViewModelStoreOwner`.
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
