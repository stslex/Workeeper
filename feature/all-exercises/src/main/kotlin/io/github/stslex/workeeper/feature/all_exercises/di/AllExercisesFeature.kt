// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl

internal typealias AllExercisesStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/all-exercises resolves its Store through the **Metro** path (KMP C.1 wave 2). PLAIN Store
 * (a BottomBar destination with no route args) — the graph exposes the Store directly and this
 * composable retains it via `rememberMetroStoreProcessor`. The 8 app-scoped deps are acquired as the
 * composition of three narrow interfaces ([StoreCoreDeps] + [NavigatorDeps] + [AllExercisesDeps] — the
 * domain tail: two repos, `resourceWrapper`, qualified `@DefaultDispatcher`) via `context.appDeps<T>()`
 * (the god-object split, mechanism A). Single `@DefaultDispatcher` (no collision), no Context.
 */
internal object AllExercisesFeature : Feature<AllExercisesStoreProcessor, AllExercises>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllExercisesStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllExercisesStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + resourceWrapper + qualified @DefaultDispatcher) from AllExercisesDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<AllExercisesDeps>()
            createGraphFactory<AllExercisesGraph.Factory>()
                .create(
                    exerciseRepository = deps.exerciseRepository,
                    tagRepository = deps.tagRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .allExercisesStore
        } as AllExercisesStoreProcessor
    }
}
