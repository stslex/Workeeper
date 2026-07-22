// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl

internal typealias AllTrainingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/all-trainings resolves its Store through the **Metro** path. PLAIN Store
 * (a BottomBar destination) — the graph exposes the Store directly and this composable retains it
 * via `rememberMetroStoreProcessor`. The 8 app-scoped bindings are acquired as the composition of three
 * narrow interfaces ([StoreCoreDeps] + [NavigatorDeps] + [AllTrainingsDeps] — the domain tail: two repos,
 * `resourceWrapper`, and the qualified `@DefaultDispatcher`) via `context.appDeps<T>()` (the god-object
 * split, mechanism A). Single `@DefaultDispatcher`, no Context.
 */
internal object AllTrainingsFeature : Feature<AllTrainingsStoreProcessor, AllTrainings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllTrainingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllTrainingsStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + resourceWrapper + qualified @DefaultDispatcher) from AllTrainingsDeps.
            // appDeps<T>() FEEDS the typed create(...) below; the @DefaultDispatcher qualifier is carried
            // through AllTrainingsDeps so Metro matches it by (type + qualifier).
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<AllTrainingsDeps>()
            createGraphFactory<AllTrainingsGraph.Factory>()
                .create(
                    trainingRepository = deps.trainingRepository,
                    tagRepository = deps.tagRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .allTrainingsStore
        } as AllTrainingsStoreProcessor
    }
}
