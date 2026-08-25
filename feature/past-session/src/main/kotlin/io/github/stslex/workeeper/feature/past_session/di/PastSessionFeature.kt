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
 * Resolves the Store through the Metro graph-extension path. The extension is created inside the
 * `rememberMetroStoreProcessor` lambda, so it lives exactly as long as the retained Store.
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
