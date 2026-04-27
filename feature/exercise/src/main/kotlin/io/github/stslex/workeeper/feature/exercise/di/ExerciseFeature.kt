// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStoreImpl

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

internal object ExerciseFeature :
    Feature<ExerciseStoreProcessor, Screen.Exercise, ExerciseComponent>() {

    @Composable
    override fun processor(
        screen: Screen.Exercise,
    ): ExerciseStoreProcessor =
        createProcessor<ExerciseStoreImpl, ExerciseStoreImpl.Factory>(screen)
}
