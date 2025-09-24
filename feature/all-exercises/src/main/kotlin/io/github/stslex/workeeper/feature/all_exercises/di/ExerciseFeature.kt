package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.State

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

internal const val EXERCISE_SCOPE_NAME = "all_exercise_scope"

/**
 * ExerciseFeature is a Koin feature module that provides the ExercisesStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore]
 * */
internal object ExerciseFeature : Feature<ExerciseStoreProcessor, AllExercisesComponent>(
    EXERCISE_SCOPE_NAME,
) {

    @Composable
    override fun processor(
        component: AllExercisesComponent,
    ): ExerciseStoreProcessor = rememberStoreProcessor(component)
}
