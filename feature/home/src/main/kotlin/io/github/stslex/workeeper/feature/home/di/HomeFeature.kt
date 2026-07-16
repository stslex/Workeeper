// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Home
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl

internal typealias HomeStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/home resolves its Store through the **Metro** path. PLAIN Store (a
 * BottomBar destination) — the graph exposes the Store directly and this composable retains it via
 * `rememberMetroStoreProcessor`. The 9 app-scoped deps are pulled from the
 * Metro AppGraph via `context.appGraphContract()`. Single `@DefaultDispatcher`, no Context.
 */
internal object HomeFeature : Feature<HomeStoreProcessor, Home>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): HomeStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<HomeStoreImpl> {
            // App-Scope Collapse Step 6 (cut): app-scope deps via the Metro AppGraphContract.
            val graph = context.appGraphContract()
            createGraphFactory<HomeGraph.Factory>()
                .create(
                    trainingRepository = graph.trainingRepository,
                    sessionRepository = graph.sessionRepository,
                    sessionConflictResolver = graph.sessionConflictResolver,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .homeStore
        } as HomeStoreProcessor
    }
}
