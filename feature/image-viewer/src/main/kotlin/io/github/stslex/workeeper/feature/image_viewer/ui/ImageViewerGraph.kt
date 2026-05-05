// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerFeature
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event

fun NavGraphBuilder.imageViewerGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ImageViewerFeature) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
            }
        }

        // Route system back through OnBackClick so haptic + analytics fire identically to
        // the icon-button path; without this BackHandler, default popBack still works but
        // skips those side effects.
        BackHandler {
            processor.consume(Action.Click.OnBackClick)
        }

        ImageViewerScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
