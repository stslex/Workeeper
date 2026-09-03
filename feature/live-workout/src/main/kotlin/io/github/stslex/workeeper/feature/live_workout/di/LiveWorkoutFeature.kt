// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl

internal typealias LiveWorkoutStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the Store through Metro's graph-extension path, built inside the
 * `rememberMetroStoreProcessor` lambda so it lives exactly as long as the Store.
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
            context.appDeps<LiveWorkoutGraph.Factory>()
                .createLiveWorkoutGraph(screen)
                .liveWorkoutStore
        } as LiveWorkoutStoreProcessor
    }
}
