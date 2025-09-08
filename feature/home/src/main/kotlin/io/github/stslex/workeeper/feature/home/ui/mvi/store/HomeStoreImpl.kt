package io.github.stslex.workeeper.feature.home.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@KoinViewModel([BaseStore::class])
@Scoped([HomeScope::class])
@Qualifier(HomeScope::class)
@Scope(HomeScope::class)
class HomeStoreImpl(
    @InjectedParam component: HomeComponent,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    appDispatcher: AppDispatcher,
) : HomeHandlerStore, BaseStore<State, Action, Event, HomeHandlerStore>(
    name = "HOME",
    initialState = State(
        items = pagingHandler.processor
    ),
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