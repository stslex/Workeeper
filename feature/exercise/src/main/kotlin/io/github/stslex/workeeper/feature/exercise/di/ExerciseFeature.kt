// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/exercise resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph and, once
 * `:app` is compiled, implements the contributed [ExerciseGraph.Factory]; `appDeps<T>()` re-narrows it
 * with its `as T` cast. All 14 formerly hand-threaded app-scoped deps are inherited from the parent, so
 * the three `appDeps` dep-interface lookups this file used to make, and the whole
 * `createGraphFactory(...).create(...)` argument list, are gone.
 *
 * **The app `Context` is now inherited too.** It used to be passed explicitly as
 * `context.applicationContext` from this composable; it now resolves from AppGraph's
 * `create(applicationContext)` bound instance, so `LocalContext` is read only to reach the
 * `AppDepsHolder` seam.
 *
 * The `Screen.Exercise` route arg is passed to the extension factory as a bound instance (shape B), so
 * the extension is built per navigation entry and carries that entry's arg — the Store needs no
 * assisted factory. The extension is created INSIDE the `rememberMetroStoreProcessor` lambda, so it is
 * built at most once per retained Store (per `NavBackStackEntry`), binding it and its
 * `@SingleIn(ExerciseScope)` nodes to exactly the Store's lifetime.
 */
internal object ExerciseFeature : FeatureAssisted<ExerciseStoreProcessor, Screen.Exercise>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Exercise): ExerciseStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ExerciseStoreImpl> {
            context.appDeps<ExerciseGraph.Factory>()
                .createExerciseGraph(screen)
                .exerciseStore
        } as ExerciseStoreProcessor
    }
}
