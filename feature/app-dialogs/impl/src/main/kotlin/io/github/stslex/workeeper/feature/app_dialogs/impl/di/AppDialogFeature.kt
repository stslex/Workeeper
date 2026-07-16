// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl

internal typealias AppDialogStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Screen-less composition entry for the app-root `AppDialogStore`. First production user of
 * `AppFeature` — see its KDoc for the mount-site invariant (must be composed as a sibling of
 * `NavHost`, never inside a destination, otherwise `LocalViewModelStoreOwner` silently rescopes the
 * Store to a `NavBackStackEntry`).
 *
 * `rememberMetroStoreProcessor` retains the Metro-created Store in whatever
 * `LocalViewModelStoreOwner` is current — here the host `ComponentActivity`'s `ViewModelStore` (root
 * mount via `AppDialogHost`). Shared app-scoped deps come from the Metro `AppGraphContract`; this
 * feature's own app-scoped impls come from the impl-internal holder seam (`appDialogInternals()`).
 */
internal object AppDialogFeature : AppFeature<AppDialogStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppDialogStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AppDialogStoreImpl> {
            // App-Scope Collapse Step 6 (cut): shared app-scope deps via the Metro AppGraphContract; this
            // feature's OWN app-scoped impls via the impl-internal holder seam (no module can name them).
            val graph = context.appGraphContract()
            val internals = context.appDialogInternals()
            createGraphFactory<AppDialogGraph.Factory>()
                .create(
                    appDialogRepository = internals.appDialogRepository,
                    appDialogObserver = internals.appDialogObserverImpl,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                )
                .appDialogStore
        } as AppDialogStoreProcessor
    }
}
