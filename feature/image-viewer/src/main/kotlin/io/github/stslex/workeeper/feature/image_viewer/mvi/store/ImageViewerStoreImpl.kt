// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStoreImpl
import io.github.stslex.workeeper.feature.image_viewer.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.image_viewer.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.image_viewer.mvi.handler.ImageViewerComponent
import io.github.stslex.workeeper.feature.image_viewer.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State

@HiltViewModel(assistedFactory = ImageViewerStoreImpl.Factory::class)
internal class ImageViewerStoreImpl @AssistedInject constructor(
    @Assisted component: ImageViewerComponent,
    clickHandler: ClickHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: ImageViewerHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(model = component.data.model),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component as NavigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<ImageViewerComponent, ImageViewerStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "ImageViewer"
    }
}
