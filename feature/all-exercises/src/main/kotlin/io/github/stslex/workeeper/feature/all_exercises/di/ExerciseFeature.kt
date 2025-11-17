package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises
import io.github.stslex.workeeper.feature.all_exercises.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl.Factory
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.State

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * ExerciseFeature is a feature module that provides the ExercisesStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore]
 * */
internal object ExerciseFeature :
    Feature<ExerciseStoreProcessor, AllExercises, AllExercisesComponent>() {

    @Composable
    override fun processor(
        screen: AllExercises,
        navigator: Navigator,
    ): ExerciseStoreProcessor = createProcessor<AllExercisesStoreImpl, Factory>(navigator, screen)

    override fun createComponent(
        navigator: Navigator,
        screen: AllExercises,
    ): AllExercisesComponent = AllExercisesComponent.create(navigator)
}
