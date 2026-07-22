// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
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
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + imageStorage + resourceWrapper + BOTH qualified dispatchers) from ExerciseDeps.
            // App Context stays direct from LocalContext (a create() param, never from the app graph).
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<ExerciseDeps>()
            createGraphFactory<ExerciseGraph.Factory>()
                .create(
                    exerciseRepository = deps.exerciseRepository,
                    tagRepository = deps.tagRepository,
                    imageStorage = deps.imageStorage,
                    personalRecordRepository = deps.personalRecordRepository,
                    sessionRepository = deps.sessionRepository,
                    trainingRepository = deps.trainingRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                    mainImmediateDispatcher = deps.mainImmediateDispatcher,
                    context = context.applicationContext,
                )
                .storeFactory
                .create(screen)
        } as ExerciseStoreProcessor
    }
}
