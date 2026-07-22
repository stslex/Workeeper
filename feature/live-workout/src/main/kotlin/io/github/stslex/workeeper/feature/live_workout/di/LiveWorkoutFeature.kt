// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl

internal typealias LiveWorkoutStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/live-workout resolves its Store through the **Metro** path. ASSISTED
 * Store (`Screen.LiveWorkout` route arg) — the graph exposes the assisted [LiveWorkoutStoreImpl.Factory]
 * and this composable calls `storeFactory.create(screen)` inside the `rememberMetroStoreProcessor`
 * lambda. The 13 app-scoped bindings are acquired as the composition of three narrow interfaces
 * ([StoreCoreDeps] + [NavigatorDeps] + [LiveWorkoutDeps] — the domain tail: seven repos,
 * `resourceWrapper`, qualified `@DefaultDispatcher`) via `context.appDeps<T>()` (the god-object split,
 * mechanism A). Single `@DefaultDispatcher`, no Context.
 */
internal object LiveWorkoutFeature : FeatureAssisted<
    LiveWorkoutStoreProcessor,
    Screen.LiveWorkout,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.LiveWorkout): LiveWorkoutStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<LiveWorkoutStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (seven repos + resourceWrapper + qualified @DefaultDispatcher) from LiveWorkoutDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<LiveWorkoutDeps>()
            createGraphFactory<LiveWorkoutGraph.Factory>()
                .create(
                    exerciseRepository = deps.exerciseRepository,
                    performedExerciseRepository = deps.performedExerciseRepository,
                    personalRecordRepository = deps.personalRecordRepository,
                    sessionRepository = deps.sessionRepository,
                    setRepository = deps.setRepository,
                    trainingExerciseRepository = deps.trainingExerciseRepository,
                    trainingRepository = deps.trainingRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .storeFactory
                .create(screen)
        } as LiveWorkoutStoreProcessor
    }
}
