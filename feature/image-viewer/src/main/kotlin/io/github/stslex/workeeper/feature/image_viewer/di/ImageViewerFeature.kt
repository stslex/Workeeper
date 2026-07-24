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
 * feature/image-viewer resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph and, once
 * `:app` is compiled, implements the contributed [ImageViewerGraph.Factory]; `appDeps<T>()` re-narrows it
 * with its `as T` cast. All 4 formerly hand-threaded app-scoped deps are inherited from the parent.
 *
 * The `Screen.ExerciseImage` route arg is passed to the extension factory as a bound instance (shape B),
 * so the extension is built per navigation entry and carries that entry's arg — the Store needs no
 * assisted factory. The extension is created INSIDE the `rememberMetroStoreProcessor` lambda, so it is
 * built at most once per retained Store (per `NavBackStackEntry`), binding it and its
 * `@SingleIn(ImageViewerScope)` nodes to exactly the Store's lifetime.
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
