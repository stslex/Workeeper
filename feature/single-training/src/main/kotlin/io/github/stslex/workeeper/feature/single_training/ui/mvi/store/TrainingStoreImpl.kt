package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.SingleTrainingComponent
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State

@HiltViewModel(assistedFactory = TrainingStoreImpl.Factory::class)
internal class TrainingStoreImpl @AssistedInject constructor(
    @Assisted navigationHandler: NavigationHandler,
    commonHandler: CommonHandler,
    inputHandler: InputHandler,
    clickHandler: ClickHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: TrainingHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME),
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.INITIAL,
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
            is Action.Input -> inputHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init(navigationHandler.uuid)),
    analytics = analytics,
    logger = logger,
) {

    @AssistedFactory
    interface Factory {
        fun create(component: SingleTrainingComponent): TrainingStoreImpl
    }

    companion object {

        @VisibleForTesting
        private const val NAME = "SingleTraining"
    }
}
