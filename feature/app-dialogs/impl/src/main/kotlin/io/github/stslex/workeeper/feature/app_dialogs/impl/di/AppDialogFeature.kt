// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
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
 * mount via `AppDialogHost`). Shared store-infra deps come from the narrow [StoreCoreDeps] interface
 * acquired via `context.appDeps<StoreCoreDeps>()` (AppGraphContract-split, mechanism A — AppDialog reads
 * only the store-infra trio, no navigator/repos); this feature's own app-scoped impls come from the
 * impl-internal holder seam (`appDialogInternals()`).
 */
internal object AppDialogFeature : AppFeature<AppDialogStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppDialogStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AppDialogStoreImpl> {
            // AppGraphContract-split (mechanism A): shared store-infra deps via the narrow StoreCoreDeps
            // interface (appDeps<T>()); this feature's OWN app-scoped impls via the impl-internal holder
            // seam (no module can name them). appDeps<T>() FEEDS the typed create(...) below.
            val deps = context.appDeps<StoreCoreDeps>()
            val internals = context.appDialogInternals()
            createGraphFactory<AppDialogGraph.Factory>()
                .create(
                    appDialogRepository = internals.appDialogRepository,
                    appDialogObserver = internals.appDialogObserverImpl,
                    storeDispatchers = deps.storeDispatchers,
                    analyticsHolder = deps.analyticsHolder,
                    loggerHolder = deps.loggerHolder,
                )
                .appDialogStore
        } as AppDialogStoreProcessor
    }
}
