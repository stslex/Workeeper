// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

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
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl

internal typealias SingleTrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/single-training resolves its Store through the **Metro** path. The Store
 * is ASSISTED — it takes the `Screen.Training` route arg — so the graph exposes the assisted
 * [SingleTrainingStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside
 * the `rememberMetroStoreProcessor` lambda (once per retained Store, per `NavBackStackEntry`).
 *
 * The app-scoped `@SingleIn(AppScope)` bindings are acquired as the composition of three narrow
 * interfaces ([StoreCoreDeps] + [NavigatorDeps] + [SingleTrainingDeps] — the domain tail: five repos,
 * `sessionConflictResolver`, `resourceWrapper`, and BOTH qualified dispatchers) via `context.appDeps<T>()`
 * (the god-object split, mechanism A). The two dispatchers cross QUALIFIED.
 * No Context — this feature injects none.
 */
internal object SingleTrainingFeature : FeatureAssisted<
    SingleTrainingStoreProcessor,
    Screen.Training,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Training): SingleTrainingStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<SingleTrainingStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + sessionConflictResolver + resourceWrapper + BOTH qualified dispatchers) from
            // SingleTrainingDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<SingleTrainingDeps>()
            createGraphFactory<SingleTrainingGraph.Factory>()
                .create(
                    trainingRepository = deps.trainingRepository,
                    trainingExerciseRepository = deps.trainingExerciseRepository,
                    exerciseRepository = deps.exerciseRepository,
                    tagRepository = deps.tagRepository,
                    sessionRepository = deps.sessionRepository,
                    sessionConflictResolver = deps.sessionConflictResolver,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                    mainImmediateDispatcher = deps.mainImmediateDispatcher,
                )
                .storeFactory
                .create(screen)
        } as SingleTrainingStoreProcessor
    }
}
