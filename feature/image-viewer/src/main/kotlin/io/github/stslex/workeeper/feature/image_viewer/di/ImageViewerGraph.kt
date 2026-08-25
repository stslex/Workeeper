// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStoreImpl

/**
 * feature/image-viewer's Metro graph: a contributed [GraphExtension] built per navigation entry,
 * with that entry's route arg as a bound instance. See the graph-extension arc HANDOFF.
 */
@GraphExtension(ImageViewerScope::class)
interface ImageViewerGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val imageViewerStore: ImageViewerStoreImpl

    @Binds
    val ImageViewerHandlerStoreImpl.bindHandlerStore: ImageViewerHandlerStore

    /**
     * GUARD: the creator method name must be unique across all contributed extension factories —
     * they all merge into `AppGraph` and two bare `create()` declarations collide.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createImageViewerGraph(@Provides screen: Screen.ExerciseImage): ImageViewerGraph
    }
}
