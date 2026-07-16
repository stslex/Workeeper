// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/exercise resolves its Store through the **Metro** path (KMP C.1 wave 1). The Store is
 * ASSISTED — it takes the `Screen.Exercise` route arg — so the graph exposes the assisted
 * [ExerciseStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside the
 * `rememberMetroStoreProcessor` lambda (once per retained Store, per `NavBackStackEntry`).
 */
internal object ExerciseFeature : FeatureAssisted<ExerciseStoreProcessor, Screen.Exercise>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Exercise): ExerciseStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ExerciseStoreImpl> {
            // App-Scope Collapse Step 6 (cut): app-scope deps via the Metro AppGraphContract; app Context
            // direct from LocalContext (a create() param of the feature graph, never from the app graph).
            val graph = context.appGraphContract()
            createGraphFactory<ExerciseGraph.Factory>()
                .create(
                    exerciseRepository = graph.exerciseRepository,
                    tagRepository = graph.tagRepository,
                    imageStorage = graph.imageStorage,
                    personalRecordRepository = graph.personalRecordRepository,
                    sessionRepository = graph.sessionRepository,
                    trainingRepository = graph.trainingRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                    mainImmediateDispatcher = graph.mainImmediateDispatcher,
                    context = context.applicationContext,
                )
                .storeFactory
                .create(screen)
        } as ExerciseStoreProcessor
    }
}
