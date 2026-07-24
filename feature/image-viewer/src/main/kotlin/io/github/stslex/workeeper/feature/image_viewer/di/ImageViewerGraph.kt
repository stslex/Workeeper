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
 * feature/image-viewer's Metro graph as a CONTRIBUTED [GraphExtension] of [ImageViewerScope]. The factory
 * carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and
 * inherits ALL of its app-scoped bindings — the 4 formerly hand-threaded bound-instance `@Provides` are
 * gone. The sole `@Binds` maps `ImageViewerHandlerStoreImpl` to `ImageViewerHandlerStore`.
 *
 * ROUTE ARG (shape B, chosen for all assisted features): the `Screen.ExerciseImage` route arg enters as a
 * `@Provides` bound instance on the extension factory rather than as an `@Assisted` store param, so the
 * accessor is the Store itself and the feature carries no assisted machinery at all. One extension is
 * built per navigation entry, parameterised by that entry's arg — see the arc HANDOFF for the measured
 * lifecycle.
 *
 * The route arg is an ordinary binding in this scope, so it COULD be injected anywhere in the extension;
 * `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor — state must flow
 * through the Store, not be read from DI.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [ImageViewerScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(ImageViewerScope::class)
interface ImageViewerGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val imageViewerStore: ImageViewerStoreImpl

    @Binds
    val ImageViewerHandlerStoreImpl.bindHandlerStore: ImageViewerHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories (all are merged
     * into `AppGraph`; two bare `create()` declarations collide). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createImageViewerGraph(@Provides screen: Screen.ExerciseImage): ImageViewerGraph
    }
}
