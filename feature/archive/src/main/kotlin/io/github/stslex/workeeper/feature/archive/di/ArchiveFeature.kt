// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
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
 * the Store's lifetime. The 8 app-scoped singletons are pulled from the Metro app graph via
 * [appGraphContract] and handed to the graph factory as bound instances.
 */
internal object ArchiveFeature : Feature<ArchiveStoreProcessor, Archive>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): ArchiveStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ArchiveStoreImpl> {
            // app-scope deps read via the Metro AppGraphContract.
            val graph = context.appGraphContract()
            createGraphFactory<ArchiveGraph.Factory>()
                .create(
                    navigator = graph.navigator,
                    exerciseRepository = graph.exerciseRepository,
                    trainingRepository = graph.trainingRepository,
                    resourceWrapper = graph.resourceWrapper,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .archiveStore
        } as ArchiveStoreProcessor
    }
}
