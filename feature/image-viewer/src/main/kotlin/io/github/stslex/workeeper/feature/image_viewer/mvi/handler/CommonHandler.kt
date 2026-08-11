// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStore
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerScope
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action

@SingleIn(ImageViewerScope::class)
internal class CommonHandler @Inject constructor(
    store: ImageViewerHandlerStore,
) : Handler<Action.Common>, ImageViewerHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> Unit
            is Action.Common.TransformChange -> processTransformChange(action)
        }
    }

    private fun processTransformChange(action: Action.Common.TransformChange) {
        // The Composable already clamps scale to [MIN_SCALE, MAX_SCALE] and pans
        // within bounds before sending — store just persists the absolute values.
        updateState {
            it.copy(
                scale = action.scale,
                offsetX = action.offsetX,
                offsetY = action.offsetY,
            )
        }
    }
}
