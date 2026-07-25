// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Home
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl

internal typealias HomeStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/home resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph, and once
 * `:app` is compiled it implements the contributed [HomeGraph.Factory]. `appDeps<T>()` re-narrows it with
 * its `as T` cast — the same acquisition seam as before, now targeting the contributed factory instead of
 * the three `XxxDeps` interfaces. (`asContribution<T>()` is not usable here: it requires a statically
 * `@DependencyGraph`-typed receiver, which the `Any` seam is not.) All 9 formerly hand-threaded
 * app-scoped deps are inherited from the parent, so `createHomeGraph()` takes no arguments.
 *
 * The extension is created INSIDE the `rememberMetroStoreProcessor` factory lambda, so it is built at
 * most once per retained [HomeStoreImpl] (per `NavBackStackEntry` `ViewModelStore`) — binding the
 * extension and its `@SingleIn(HomeScope)` nodes to exactly the Store's lifetime.
 */
internal object HomeFeature : Feature<HomeStoreProcessor, Home>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): HomeStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<HomeStoreImpl> {
            context.appDeps<HomeGraph.Factory>()
                .createHomeGraph()
                .homeStore
        } as HomeStoreProcessor
    }
}
