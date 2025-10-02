package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.EntryPointAccessors
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

internal typealias ExerciseProcessor = StoreProcessor<State, Action, Event>

internal const val EXERCISE_SCOPE_NAME = "exercise_scope"

/**
 * ExerciseFeature is a Hilt feature module that provides the ExerciseDialogStore processor.
 * It is responsible for managing the state and actions related to the exercise feature.
 *
 * @see [io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore]
 * */
internal object ExerciseFeature : Feature<ExerciseProcessor, ExerciseComponent>(
    scopeName = EXERCISE_SCOPE_NAME,
) {

    @Composable
    override fun processor(
        component: ExerciseComponent,
    ): ExerciseProcessor {
        val context = LocalContext.current
        val entryPoint = EntryPointAccessors.fromActivity(
            context as android.app.Activity,
            ExerciseEntryPoint::class.java
        )
        val factory = entryPoint.exerciseStoreFactory()
        return rememberStoreProcessor<State, Action, Event, ExerciseStoreImpl, ExerciseComponent>(
            component = component,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return factory.create(component) as T
                }
            },
            key = component.hashCode().toString(),
        )
    }
}
