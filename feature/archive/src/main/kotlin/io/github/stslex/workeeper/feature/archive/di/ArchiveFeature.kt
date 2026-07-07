// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import dev.zacsweers.metro.createGraphFactory
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
 * feature/archive resolves its Store through the **Metro** path (KMP C.1 M0), not
 * `hiltViewModel()`. It is the first feature flipped to Metro; the other 11 features + the
 * app stay on Hilt via the same [rememberStoreProcessor][io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor]
 * seam.
 *
 * The Metro graph is built INSIDE the `rememberMetroStoreProcessor` factory lambda so it is
 * created at most once per retained [ArchiveStoreImpl] (per `NavBackStackEntry`
 * `ViewModelStore`) — binding the graph and its `@SingleIn(ArchiveScope)` nodes to exactly
 * the Store's lifetime, the way Hilt `@ViewModelScoped` did. The 8 app-scoped Hilt singletons
 * are pulled from the Hilt `SingletonComponent` via [ArchiveHiltEntryPoint] and handed to the
 * graph factory as bound instances.
 */
internal object ArchiveFeature : Feature<ArchiveStoreProcessor, Archive>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): ArchiveStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ArchiveStoreImpl> {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ArchiveHiltEntryPoint::class.java,
            )
            createGraphFactory<ArchiveGraph.Factory>()
                .create(
                    navigator = entryPoint.navigator(),
                    exerciseRepository = entryPoint.exerciseRepository(),
                    trainingRepository = entryPoint.trainingRepository(),
                    resourceWrapper = entryPoint.resourceWrapper(),
                    storeDispatchers = entryPoint.storeDispatchers(),
                    analyticsHolder = entryPoint.analyticsHolder(),
                    loggerHolder = entryPoint.loggerHolder(),
                    defaultDispatcher = entryPoint.defaultDispatcher(),
                )
                .archiveStore
        } as ArchiveStoreProcessor
    }
}
