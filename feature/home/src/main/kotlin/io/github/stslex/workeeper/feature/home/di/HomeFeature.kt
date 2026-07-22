// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Home
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl

internal typealias HomeStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/home resolves its Store through the **Metro** path. PLAIN Store (a
 * BottomBar destination) — the graph exposes the Store directly and this composable retains it via
 * `rememberMetroStoreProcessor`. The 9 app-scoped deps are acquired as the composition of three narrow
 * interfaces ([StoreCoreDeps] + [NavigatorDeps] + [HomeDeps] — the domain tail: two repos,
 * `sessionConflictResolver`, `resourceWrapper`, qualified `@DefaultDispatcher`) via `context.appDeps<T>()`
 * (the god-object split, mechanism A). Single `@DefaultDispatcher`, no Context.
 */
internal object HomeFeature : Feature<HomeStoreProcessor, Home>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): HomeStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<HomeStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + sessionConflictResolver + resourceWrapper + qualified @DefaultDispatcher) from
            // HomeDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<HomeDeps>()
            createGraphFactory<HomeGraph.Factory>()
                .create(
                    trainingRepository = deps.trainingRepository,
                    sessionRepository = deps.sessionRepository,
                    sessionConflictResolver = deps.sessionConflictResolver,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .homeStore
        } as HomeStoreProcessor
    }
}
