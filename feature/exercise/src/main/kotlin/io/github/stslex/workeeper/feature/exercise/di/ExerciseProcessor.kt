package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

internal typealias ExerciseProcessor = StoreProcessor<State, Action, Event>

internal const val EXERCISE_SCOPE_NAME = "exercise_scope"

/**
 * ExerciseFeature is a Koin feature module that provides the ExerciseDialogStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore]
 * */
internal object ExerciseFeature : Feature<ExerciseProcessor, ExerciseComponent>(
    scopeName = EXERCISE_SCOPE_NAME
) {

    @Composable
    override fun processor(
        component: ExerciseComponent
    ): ExerciseProcessor = rememberStoreProcessor(
        component = component,
        key = component.hashCode().toString()
    )
}