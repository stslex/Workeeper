// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStoreImpl

internal typealias ImageViewerStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the Store through the Metro graph-extension path. The extension is created inside the
 * `rememberMetroStoreProcessor` lambda, so its scope is exactly the retained Store's lifetime.
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
            context.appDeps<ImageViewerGraph.Factory>()
                .createImageViewerGraph(screen)
                .imageViewerStore
        } as ImageViewerStoreProcessor
    }
}
