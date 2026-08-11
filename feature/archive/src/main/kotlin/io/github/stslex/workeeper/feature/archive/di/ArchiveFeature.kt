// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Archive
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl

internal typealias ArchiveStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/archive resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph, and once
 * `:app` is compiled it implements the contributed [ArchiveGraph.Factory]. `appDeps<T>()` re-narrows it
 * with its `as T` cast — the same acquisition seam as before, now targeting the contributed factory
 * instead of an `XxxDeps` interface. (`asContribution<T>()` is not usable here: it requires a statically
 * `@DependencyGraph`-typed receiver, which the `Any` seam is not.) All 8 formerly hand-threaded
 * app-scoped deps are inherited from the parent, so `createArchiveGraph()` takes no arguments.
 *
 * The extension is created INSIDE the `rememberMetroStoreProcessor` factory lambda, so it is built at
 * most once per retained [ArchiveStoreImpl] (per `NavBackStackEntry` `ViewModelStore`) — binding the
 * extension and its `@SingleIn(ArchiveScope)` nodes to exactly the Store's lifetime.
 */
internal object ArchiveFeature : Feature<ArchiveStoreProcessor, Archive>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): ArchiveStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ArchiveStoreImpl> {
            context.appDeps<ArchiveGraph.Factory>()
                .createArchiveGraph()
                .archiveStore
        } as ArchiveStoreProcessor
    }
}
