// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen.Archive
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl

internal typealias ArchiveStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/archive resolves its Store through the Metro `rememberMetroStoreProcessor` seam.
 *
 * The Metro graph is built INSIDE the `rememberMetroStoreProcessor` factory lambda so it is
 * created at most once per retained [ArchiveStoreImpl] (per `NavBackStackEntry`
 * `ViewModelStore`) — binding the graph and its `@SingleIn(ArchiveScope)` nodes to exactly
 * the Store's lifetime. The 8 app-scoped singletons are acquired as the composition of three narrow
 * interfaces ([StoreCoreDeps] + [NavigatorDeps] + [ArchiveDeps] — the domain tail: two repos,
 * `resourceWrapper`, qualified `@DefaultDispatcher`) via `context.appDeps<T>()` (the god-object split,
 * mechanism A) and handed to the graph factory as bound instances.
 */
internal object ArchiveFeature : Feature<ArchiveStoreProcessor, Archive>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): ArchiveStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ArchiveStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + resourceWrapper + qualified @DefaultDispatcher) from ArchiveDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<ArchiveDeps>()
            createGraphFactory<ArchiveGraph.Factory>()
                .create(
                    navigator = navDeps.navigator,
                    exerciseRepository = deps.exerciseRepository,
                    trainingRepository = deps.trainingRepository,
                    resourceWrapper = deps.resourceWrapper,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .archiveStore
        } as ArchiveStoreProcessor
    }
}
