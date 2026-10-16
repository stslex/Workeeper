// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllExercises
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl

internal typealias AllExercisesStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the Store through the Metro graph-extension path. The extension is created inside the
 * `rememberMetroStoreProcessor` lambda, so its scope is exactly the retained Store's lifetime.
 */
internal class AllExercisesFeature(
    private val factory: AllExercisesGraph.Factory,
) : Feature<AllExercisesStoreProcessor, AllExercises>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllExercisesStoreProcessor {
        return rememberMetroStoreProcessor<AllExercisesStoreImpl> {
            factory
                .createAllExercisesGraph()
                .allExercisesStore
        } as AllExercisesStoreProcessor
    }
}
