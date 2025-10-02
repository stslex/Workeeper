package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store

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
    @Assisted navigationHandler: AllTrainingsComponent,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: TrainingHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(
        pagingUiState = pagingHandler.pagingUiState,
    ),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler as NavigationHandler
            is Action.Paging -> pagingHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<AllTrainingsComponent, TrainingStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "AllTrainings"
    }
}
