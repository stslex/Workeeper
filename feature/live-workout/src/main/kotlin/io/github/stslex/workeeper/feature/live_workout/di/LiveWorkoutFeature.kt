// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
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
 * lambda. The 13 app-scoped bindings are pulled from the Metro app graph via
 * [context.appGraphContract()]. Single `@DefaultDispatcher`, no Context.
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
            // app-scope deps read via the Metro AppGraphContract.
            val graph = context.appGraphContract()
            createGraphFactory<LiveWorkoutGraph.Factory>()
                .create(
                    exerciseRepository = graph.exerciseRepository,
                    performedExerciseRepository = graph.performedExerciseRepository,
                    personalRecordRepository = graph.personalRecordRepository,
                    sessionRepository = graph.sessionRepository,
                    setRepository = graph.setRepository,
                    trainingExerciseRepository = graph.trainingExerciseRepository,
                    trainingRepository = graph.trainingRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .storeFactory
                .create(screen)
        } as LiveWorkoutStoreProcessor
    }
}
