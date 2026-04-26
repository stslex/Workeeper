// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises
import io.github.stslex.workeeper.feature.all_exercises.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl

internal typealias AllExercisesStoreProcessor = StoreProcessor<State, Action, Event>

internal object AllExercisesFeature :
    Feature<AllExercisesStoreProcessor, AllExercises, AllExercisesComponent>() {

    @Composable
    override fun processor(
        screen: AllExercises,
    ): AllExercisesStoreProcessor =
        createProcessor<AllExercisesStoreImpl, AllExercisesStoreImpl.Factory>(screen)
}
