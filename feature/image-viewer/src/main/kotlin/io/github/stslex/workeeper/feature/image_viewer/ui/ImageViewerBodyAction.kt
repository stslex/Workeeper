// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.ui

import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action

internal sealed interface ImageViewerBodyAction {

    data object BackClick : ImageViewerBodyAction

    data object DoubleTap : ImageViewerBodyAction

    data class TransformChange(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
    ) : ImageViewerBodyAction
}

internal fun ImageViewerBodyAction.toAction(): Action = when (this) {
    ImageViewerBodyAction.BackClick -> Action.Click.OnBackClick
    ImageViewerBodyAction.DoubleTap -> Action.Click.OnDoubleTap
    is ImageViewerBodyAction.TransformChange -> Action.Common.TransformChange(
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
    )
}
