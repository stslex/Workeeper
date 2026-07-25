// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl

internal typealias AllExercisesStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/all-exercises resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph, and once
 * `:app` is compiled it implements the contributed [AllExercisesGraph.Factory]. `appDeps<T>()` re-narrows
 * it with its `as T` cast — the same acquisition seam as before, now targeting the contributed factory
 * instead of the three `XxxDeps` interfaces. (`asContribution<T>()` is not usable here: it requires a
 * statically `@DependencyGraph`-typed receiver, which the `Any` seam is not.) All 8 formerly
 * hand-threaded app-scoped deps are inherited from the parent, so `createAllExercisesGraph()` takes no
 * arguments.
 *
 * The extension is created INSIDE the `rememberMetroStoreProcessor` factory lambda, so it is built at
 * most once per retained [AllExercisesStoreImpl] (per `NavBackStackEntry` `ViewModelStore`) — binding the
 * extension and its `@SingleIn(AllExercisesScope)` nodes to exactly the Store's lifetime.
 */
internal object AllExercisesFeature : Feature<AllExercisesStoreProcessor, AllExercises>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllExercisesStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllExercisesStoreImpl> {
            context.appDeps<AllExercisesGraph.Factory>()
                .createAllExercisesGraph()
                .allExercisesStore
        } as AllExercisesStoreProcessor
    }
}
