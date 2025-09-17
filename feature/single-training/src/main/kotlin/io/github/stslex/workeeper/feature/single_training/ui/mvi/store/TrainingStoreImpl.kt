package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel(binds = [BaseStore::class])
@Qualifier(name = TRAINING_SCOPE_NAME)
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingStoreImpl(
    @InjectedParam navigationHandler: NavigationHandler,
    commonHandler: CommonHandler,
    dispatcher: AppDispatcher,
    @Named(TRAINING_SCOPE_NAME) handlerStore: TrainingHandlerStoreImpl
) : BaseStore<State, Action, Event>(
    name = "SingleTraining",
    initialState = State.INITIAL,
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
        }
    },
    storeEmitter = handlerStore,
    appDispatcher = dispatcher,
    initialActions = listOf(Action.Common.Init(navigationHandler.uuid))
)