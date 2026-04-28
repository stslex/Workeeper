// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.LiveWorkoutComponent
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl

internal typealias LiveWorkoutStoreProcessor = StoreProcessor<State, Action, Event>

internal object LiveWorkoutFeature :
    Feature<LiveWorkoutStoreProcessor, Screen.LiveWorkout, LiveWorkoutComponent>() {

    @Composable
    override fun processor(
        screen: Screen.LiveWorkout,
    ): LiveWorkoutStoreProcessor =
        createProcessor<LiveWorkoutStoreImpl, LiveWorkoutStoreImpl.Factory>(screen)
}
