// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl

internal typealias PastSessionStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/past-session resolves its Store through the **Metro** path. ASSISTED
 * Store (`Screen.PastSession` route arg) — the graph exposes the assisted
 * [PastSessionStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside the
 * `rememberMetroStoreProcessor` lambda. The 9 app-scoped `@SingleIn(AppScope)` bindings are pulled
 * from the Metro AppGraph via [appGraphContract]. Single `@IODispatcher` (no collision), no Context.
 */
internal object PastSessionFeature : FeatureAssisted<
    PastSessionStoreProcessor,
    Screen.PastSession,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.PastSession): PastSessionStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<PastSessionStoreImpl> {
            // app-scope deps read via the Metro AppGraphContract.
            val graph = context.appGraphContract()
            createGraphFactory<PastSessionGraph.Factory>()
                .create(
                    sessionRepository = graph.sessionRepository,
                    setRepository = graph.setRepository,
                    personalRecordRepository = graph.personalRecordRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    ioDispatcher = graph.ioDispatcher,
                )
                .storeFactory
                .create(screen)
        } as PastSessionStoreProcessor
    }
}
