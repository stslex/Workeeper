// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStoreImpl

/**
 * The single Metro dependency graph for feature/image-viewer. Scoped to [ImageViewerScope].
 *
 * ASSISTED Store: `ImageViewerStoreImpl` takes the `Screen.ExerciseImage` route arg via `@Assisted`,
 * so the graph exposes the assisted [ImageViewerStoreImpl.Factory] as its root — never the Store.
 *
 * The 4 app-scoped deps are `@SingleIn(AppScope)` bindings from the app graph, handed in as
 * `@Provides` bound instances. The one `@Binds` (ImageViewerHandlerStore). No dispatcher, no Context.
 */
@DependencyGraph(scope = ImageViewerScope::class)
internal interface ImageViewerGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: ImageViewerStoreImpl.Factory

    @Binds
    val ImageViewerHandlerStoreImpl.bindHandlerStore: ImageViewerHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
        ): ImageViewerGraph
    }
}
