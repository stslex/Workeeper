// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl

internal typealias AllTrainingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/all-trainings resolves its Store through the **Metro** graph-extension path. PLAIN Store
 * (a BottomBar destination) — the extension exposes the Store directly and this composable retains it
 * via `rememberMetroStoreProcessor`.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph, and once
 * `:app` is compiled it implements the contributed [AllTrainingsGraph.Factory]. `appDeps<T>()` re-narrows
 * it with its `as T` cast — the same acquisition seam used before, now targeting the contributed factory
 * instead of a `XxxDeps` interface. (`asContribution<T>()` is not usable here: it requires a statically
 * `@DependencyGraph`-typed receiver, which the `Any` seam is not.) All 8 formerly hand-threaded app-scoped
 * deps are inherited from the parent graph, so `create()` takes no arguments.
 */
internal object AllTrainingsFeature : Feature<AllTrainingsStoreProcessor, AllTrainings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllTrainingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllTrainingsStoreImpl> {
            context.appDeps<AllTrainingsGraph.Factory>()
                .create()
                .allTrainingsStore
        } as AllTrainingsStoreProcessor
    }
}
