// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the exercise Store through Metro's graph-extension path, with the route arg bound in.
 * Built inside `rememberMetroStoreProcessor`, so it shares the retained Store's lifetime.
 */
internal object ExerciseFeature : FeatureAssisted<ExerciseStoreProcessor, Screen.Exercise>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Exercise): ExerciseStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ExerciseStoreImpl> {
            context.appDeps<ExerciseGraph.Factory>()
                .createExerciseGraph(screen)
                .exerciseStore
        } as ExerciseStoreProcessor
    }
}
