// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.single_training.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.single_training.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State

// Metro assisted Store: @AssistedInject constructs it with the Screen.Training route arg via
// @Assisted; the graph exposes the @AssistedFactory (never the Store). No Hilt @HiltViewModel.
// Retention is owned by the Android ViewModelStore via rememberMetroStoreProcessor — no @SingleIn.
@AssistedInject
internal class SingleTrainingStoreImpl(
    @Assisted screen: Screen.Training,
    navigationHandler: NavigationHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: SingleTrainingHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(uuid = screen.uuid),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
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
    interface Factory : StoreFactory<Screen.Training, SingleTrainingStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "SingleTraining"
    }
}
