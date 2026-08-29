// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerFeature
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event

fun NavGraphScope.imageViewerGraph(
    factory: ImageViewerGraph.Factory,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ImageViewerFeature(factory)) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
            }
        }

        ImageViewerScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
