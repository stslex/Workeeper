// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl

internal typealias SingleTrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/single-training resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph and, once
 * `:app` is compiled, implements the contributed [SingleTrainingGraph.Factory]; `appDeps<T>()`
 * re-narrows it with its `as T` cast. All 13 formerly hand-threaded app-scoped deps are inherited from
 * the parent, so the three `appDeps` dep-interface lookups this file used to make
 * and the whole `createGraphFactory(...).create(...)` argument list are gone — including both qualified
 * dispatchers, which now cross the graph boundary as two distinct keys rather than being handed in.
 *
 * The `Screen.Training` route arg is passed to the extension factory as a bound instance (shape B), so
 * the extension is built per navigation entry and carries that entry's arg — the Store needs no
 * assisted factory. The extension is created INSIDE the `rememberMetroStoreProcessor` lambda, so it is
 * built at most once per retained Store (per `NavBackStackEntry`), binding it and its
 * `@SingleIn(SingleTrainingScope)` nodes to exactly the Store's lifetime.
 */
internal object SingleTrainingFeature : FeatureAssisted<
    SingleTrainingStoreProcessor,
    Screen.Training,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Training): SingleTrainingStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<SingleTrainingStoreImpl> {
            context.appDeps<SingleTrainingGraph.Factory>()
                .createSingleTrainingGraph(screen)
                .singleTrainingStore
        } as SingleTrainingStoreProcessor
    }
}
