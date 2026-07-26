// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl

internal typealias PastSessionStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/past-session resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph and, once
 * `:app` is compiled, implements the contributed [PastSessionGraph.Factory]; `appDeps<T>()` re-narrows it
 * with its `as T` cast. All 9 formerly hand-threaded app-scoped deps are inherited from the parent, so
 * the three `appDeps` dep-interface lookups this file used to make, and the whole
 * `createGraphFactory(...).create(...)` argument list, are gone.
 *
 * The `Screen.PastSession` route arg is passed to the extension factory as a bound instance (shape B),
 * so the extension is built per navigation entry and carries that entry's arg — the Store needs no
 * assisted factory. The extension is created INSIDE the `rememberMetroStoreProcessor` lambda, so it is
 * built at most once per retained Store (per `NavBackStackEntry`), binding it and its
 * `@SingleIn(PastSessionScope)` nodes to exactly the Store's lifetime.
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
            context.appDeps<PastSessionGraph.Factory>()
                .createPastSessionGraph(screen)
                .pastSessionStore
        } as PastSessionStoreProcessor
    }
}
