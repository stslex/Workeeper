// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Home
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl

internal typealias HomeStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/home resolves its Store through the Metro graph-extension path. The extension is created
 * inside the `rememberMetroStoreProcessor` lambda, so it lives exactly as long as the Store.
 */
internal object HomeFeature : Feature<HomeStoreProcessor, Home>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): HomeStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<HomeStoreImpl> {
            context.appDeps<HomeGraph.Factory>()
                .createHomeGraph()
                .homeStore
        } as HomeStoreProcessor
    }
}
