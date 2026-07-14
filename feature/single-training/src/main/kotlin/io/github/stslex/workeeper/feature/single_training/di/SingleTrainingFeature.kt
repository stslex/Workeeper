// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl

internal typealias SingleTrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/single-training resolves its Store through the **Metro** path (KMP C.1 wave 1). The Store
 * is ASSISTED — it takes the `Screen.Training` route arg — so the graph exposes the assisted
 * [SingleTrainingStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside
 * the `rememberMetroStoreProcessor` lambda (once per retained Store, per `NavBackStackEntry`).
 *
 * The 13 app-scoped Hilt singletons are pulled from the `SingletonComponent` via
 * [SingleTrainingHiltEntryPoint]. The two dispatchers cross the bridge QUALIFIED (`includeJavax`).
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
            // App-Scope Collapse Step 6 (cut): app-scope deps via the Metro AppGraphContract.
            val graph = context.appGraphContract()
            createGraphFactory<SingleTrainingGraph.Factory>()
                .create(
                    trainingRepository = graph.trainingRepository,
                    trainingExerciseRepository = graph.trainingExerciseRepository,
                    exerciseRepository = graph.exerciseRepository,
                    tagRepository = graph.tagRepository,
                    sessionRepository = graph.sessionRepository,
                    sessionConflictResolver = graph.sessionConflictResolver,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                    mainImmediateDispatcher = graph.mainImmediateDispatcher,
                )
                .storeFactory
                .create(screen)
        } as SingleTrainingStoreProcessor
    }
}
