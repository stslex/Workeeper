// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.image_viewer.mvi.handler.ImageViewerComponent
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStoreImpl

internal typealias ImageViewerStoreProcessor = StoreProcessor<State, Action, Event>

internal object ImageViewerFeature :
    Feature<ImageViewerStoreProcessor, Screen.ExerciseImage, ImageViewerComponent>() {

    @Composable
    override fun processor(
        screen: Screen.ExerciseImage,
    ): ImageViewerStoreProcessor =
        createProcessor<ImageViewerStoreImpl, ImageViewerStoreImpl.Factory>(screen)
}
