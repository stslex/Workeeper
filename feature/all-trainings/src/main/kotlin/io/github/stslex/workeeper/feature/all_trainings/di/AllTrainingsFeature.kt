// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl

internal typealias AllTrainingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/all-trainings resolves its Store through the **Metro** path. PLAIN Store
 * (a BottomBar destination) — the graph exposes the Store directly and this composable retains it
 * via `rememberMetroStoreProcessor`. The 8 app-scoped bindings are pulled from the Metro app graph
 * via `context.appGraphContract()`. Single `@DefaultDispatcher`, no Context.
 */
internal object AllTrainingsFeature : Feature<AllTrainingsStoreProcessor, AllTrainings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllTrainingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllTrainingsStoreImpl> {
            val graph = context.appGraphContract()
            createGraphFactory<AllTrainingsGraph.Factory>()
                .create(
                    trainingRepository = graph.trainingRepository,
                    tagRepository = graph.tagRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .allTrainingsStore
        } as AllTrainingsStoreProcessor
    }
}
