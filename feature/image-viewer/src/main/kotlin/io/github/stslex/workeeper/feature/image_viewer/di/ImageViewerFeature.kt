// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

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
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStoreImpl

internal typealias ImageViewerStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/image-viewer resolves its Store through the **Metro** path. ASSISTED
 * Store (`Screen.ExerciseImage` route arg) — the graph exposes the assisted
 * [ImageViewerStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside the
 * `rememberMetroStoreProcessor` lambda. The 4 app-scoped singletons are acquired as the composition of
 * two narrow interfaces ([StoreCoreDeps] {analytics, logger, dispatchers} + [NavigatorDeps] {navigator})
 * via `context.appDeps<T>()` (the god-object split, mechanism A). No dispatcher, no Context.
 */
internal object ImageViewerFeature : FeatureAssisted<
    ImageViewerStoreProcessor,
    Screen.ExerciseImage,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.ExerciseImage): ImageViewerStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ImageViewerStoreImpl> {
            // Mechanism A (the god-object split): the spine four come from the composition of two narrow
            // interfaces (appDeps<T>() FEEDS the typed create(...) below). No domain tail → no XDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            createGraphFactory<ImageViewerGraph.Factory>()
                .create(
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                )
                .storeFactory
                .create(screen)
        } as ImageViewerStoreProcessor
    }
}
