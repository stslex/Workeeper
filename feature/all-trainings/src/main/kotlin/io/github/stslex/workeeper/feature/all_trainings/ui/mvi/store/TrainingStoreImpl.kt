package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store

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
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State

@HiltViewModel(assistedFactory = TrainingStoreImpl.Factory::class)
internal class TrainingStoreImpl @AssistedInject constructor(
    @Assisted navigationHandler: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: TrainingHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME),
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(
        pagingUiState = pagingHandler.pagingUiState,
    ),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Paging -> pagingHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    analytics = analytics,
    logger = logger,
) {

    @AssistedFactory
    interface Factory {
        fun create(component: AllTrainingsComponent): TrainingStoreImpl
    }

    companion object {

        @VisibleForTesting
        private const val NAME = "AllTrainings"
    }
}
