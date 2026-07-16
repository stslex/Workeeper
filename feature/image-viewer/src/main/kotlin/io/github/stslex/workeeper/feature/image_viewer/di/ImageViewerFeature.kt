// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
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
 * `rememberMetroStoreProcessor` lambda. The 4 app-scoped singletons are pulled from the
 * Metro app graph via [appGraphContract]. No dispatcher, no Context.
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
            val graph = context.appGraphContract()
            createGraphFactory<ImageViewerGraph.Factory>()
                .create(
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                )
                .storeFactory
                .create(screen)
        } as ImageViewerStoreProcessor
    }
}
