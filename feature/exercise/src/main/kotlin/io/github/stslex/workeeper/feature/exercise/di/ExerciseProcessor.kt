package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

internal typealias ExerciseProcessor = StoreProcessor<State, Action, Event>

/**
 * ExerciseFeature is a Hilt feature module that provides the ExerciseDialogStore processor.
 * It is responsible for managing the state and actions related to the exercise feature.
 *
 * @see [io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore]
 * */
internal data object ExerciseFeature : Feature<ExerciseProcessor, Exercise, ExerciseComponent>() {

    @Composable
    override fun processor(
        screen: Exercise,
    ): ExerciseProcessor = createProcessor<ExerciseStoreImpl, ExerciseStoreImpl.Factory>(
        screen = screen,
    )
}
