// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl

internal typealias AppDialogStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Screen-less composition entry for the app-root `AppDialogStore`, resolved through the **Metro**
 * path (KMP C.1 wave 4). First production user of `AppFeature` — see its KDoc for the mount-site
 * invariant (must be composed as a sibling of `NavHost`, never inside a destination, otherwise
 * `LocalViewModelStoreOwner` silently rescopes the Store to a `NavBackStackEntry`).
 *
 * The DI backend flip does NOT change the mount mechanic: `rememberMetroStoreProcessor` retains the
 * Metro-created Store in whatever `LocalViewModelStoreOwner` is current — here the host
 * `ComponentActivity`'s `ViewModelStore` (root mount via `AppDialogHost`), exactly as the Hilt path
 * did. The 5 app-scoped Hilt singletons are pulled from the `SingletonComponent` via
 * [AppDialogsHiltEntryPoint]. No dispatcher, no Context (the only Context is `@ApplicationContext` on
 * the Hilt-constructed `@Singleton` `AppDialogRepository` — Hilt-side only).
 */
internal object AppDialogFeature : AppFeature<AppDialogStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppDialogStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AppDialogStoreImpl> {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppDialogsHiltEntryPoint::class.java,
            )
            createGraphFactory<AppDialogGraph.Factory>()
                .create(
                    appDialogRepository = entryPoint.appDialogRepository(),
                    appDialogObserver = entryPoint.appDialogObserverImpl(),
                    storeDispatchers = entryPoint.storeDispatchers(),
                    analyticsHolder = entryPoint.analyticsHolder(),
                    loggerHolder = entryPoint.loggerHolder(),
                )
                .appDialogStore
        } as AppDialogStoreProcessor
    }
}
