// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.store

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
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State

@HiltViewModel(assistedFactory = AllTrainingsStoreImpl.Factory::class)
internal class AllTrainingsStoreImpl @AssistedInject constructor(
    @Assisted component: AllTrainingsComponent,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: AllTrainingsHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(pagingUiState = pagingHandler.pagingUiState),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component as NavigationHandler
            is Action.Paging -> pagingHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Paging.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<AllTrainingsComponent, AllTrainingsStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "AllTrainings"
    }
}
