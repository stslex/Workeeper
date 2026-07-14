// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl

internal typealias AllExercisesStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/all-exercises resolves its Store through the **Metro** path (KMP C.1 wave 2). PLAIN Store
 * (a BottomBar destination with no route args) — the graph exposes the Store directly and this
 * composable retains it via `rememberMetroStoreProcessor`. The 8 app-scoped Hilt singletons are
 * pulled from the `SingletonComponent` via [AllExercisesHiltEntryPoint]. Single `@DefaultDispatcher`
 * (no collision), no Context.
 */
internal object AllExercisesFeature : Feature<AllExercisesStoreProcessor, AllExercises>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllExercisesStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllExercisesStoreImpl> {
            // App-Scope Collapse Step 6 (P-BRIDGES): app-scope deps read via the Metro AppGraphContract
            // (Hilt-free), replacing EntryPointAccessors + AllExercisesHiltEntryPoint. The graph is the
            // SAME app graph the EntryPoint delegated to — identical bindings, Hilt-free read path. The
            // AllExercisesHiltEntryPoint declaration stays until the cut (dead once this reader repoints).
            val graph = context.appGraphContract()
            createGraphFactory<AllExercisesGraph.Factory>()
                .create(
                    exerciseRepository = graph.exerciseRepository,
                    tagRepository = graph.tagRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .allExercisesStore
        } as AllExercisesStoreProcessor
    }
}
