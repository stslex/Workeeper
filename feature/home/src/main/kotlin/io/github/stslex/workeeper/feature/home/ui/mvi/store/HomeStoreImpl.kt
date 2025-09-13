package io.github.stslex.workeeper.feature.home.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.feature.home.di.HOME_SCOPE_NAME
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStoreImpl
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel([BaseStore::class])
@Qualifier(name = HOME_SCOPE_NAME)
@Scope(name = HOME_SCOPE_NAME)
internal class HomeStoreImpl(
    @InjectedParam component: HomeComponent,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    appDispatcher: AppDispatcher,
    @Named(HOME_SCOPE_NAME) storeEmitter: HomeHandlerStoreImpl
) : BaseStore<State, Action, Event>(
    name = "HOME",
    initialState = State.init(
        allItems = pagingHandler.processor
    ),
    storeEmitter = storeEmitter,
    appDispatcher = appDispatcher,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> component
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    initialActions = listOf(Action.Paging.Init)
)