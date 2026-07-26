// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
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
 * mount via `AppDialogHost`).
 *
 * All 5 formerly hand-threaded deps are inherited from the parent graph, so BOTH former acquisition
 * paths are gone: the `appDeps` dep-interface lookup for the store-infra trio, AND the
 * impl-internal `appDialogInternals()` holder seam that fed this feature its own app-scoped
 * singletons. The extension inherits `AppDialogRepository` and `AppDialogObserverImpl` straight from
 * `AppGraph`, so that seam is deleted outright rather than narrowed.
 *
 * The creator takes no arguments — this feature is screen-less, so there is no route arg to bind.
 */
internal object AppDialogFeature : AppFeature<AppDialogStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppDialogStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AppDialogStoreImpl> {
            context.appDeps<AppDialogGraph.Factory>()
                .createAppDialogGraph()
                .appDialogStore
        } as AppDialogStoreProcessor
    }
}
