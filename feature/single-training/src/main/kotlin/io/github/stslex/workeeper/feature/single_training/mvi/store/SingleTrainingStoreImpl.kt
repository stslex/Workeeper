// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

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
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.single_training.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.SingleTrainingComponent
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State

@HiltViewModel(assistedFactory = SingleTrainingStoreImpl.Factory::class)
internal class SingleTrainingStoreImpl @AssistedInject constructor(
    @Assisted component: SingleTrainingComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: SingleTrainingHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(uuid = component.data.uuid),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component as NavigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<SingleTrainingComponent, SingleTrainingStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "SingleTraining"
    }
}
