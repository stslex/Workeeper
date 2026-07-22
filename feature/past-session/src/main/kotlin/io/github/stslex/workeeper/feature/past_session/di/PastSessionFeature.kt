// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

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
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl

internal typealias PastSessionStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/past-session resolves its Store through the **Metro** path. ASSISTED
 * Store (`Screen.PastSession` route arg) — the graph exposes the assisted
 * [PastSessionStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside the
 * `rememberMetroStoreProcessor` lambda. The 9 app-scoped `@SingleIn(AppScope)` bindings are acquired as
 * the composition of three narrow interfaces ([StoreCoreDeps] + [NavigatorDeps] + [PastSessionDeps] — the
 * domain tail: three repos, `resourceWrapper`, qualified `@IODispatcher`) via `context.appDeps<T>()` (the
 * god-object split, mechanism A). Single `@IODispatcher` (no collision), no Context.
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
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + resourceWrapper + qualified @IODispatcher) from PastSessionDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<PastSessionDeps>()
            createGraphFactory<PastSessionGraph.Factory>()
                .create(
                    sessionRepository = deps.sessionRepository,
                    setRepository = deps.setRepository,
                    personalRecordRepository = deps.personalRecordRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    ioDispatcher = deps.ioDispatcher,
                )
                .storeFactory
                .create(screen)
        } as PastSessionStoreProcessor
    }
}
