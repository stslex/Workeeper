package io.github.stslex.workeeper.feature.charts.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStoreImpl
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel([BaseStore::class])
@Qualifier(name = CHARTS_SCOPE_NAME)
@Scope(name = CHARTS_SCOPE_NAME)
internal class ChartsStoreImpl(
    @InjectedParam navigationHandler: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    appDispatcher: AppDispatcher,
    @Named(CHARTS_SCOPE_NAME) storeEmitter: ChartsHandlerStoreImpl
) : BaseStore<State, Action, Event>(
    name = "HOME",
    initialState = State.INITIAL,
    storeEmitter = storeEmitter,
    appDispatcher = appDispatcher,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> navigationHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    initialActions = listOf(Action.Paging.Init)
)