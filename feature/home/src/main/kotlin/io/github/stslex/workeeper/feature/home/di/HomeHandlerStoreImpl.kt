package io.github.stslex.workeeper.feature.home.di

import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [HomeHandlerStore::class, HandlerStoreEmitter::class])
@Scope(name = HOME_SCOPE_NAME)
@Named(HOME_SCOPE_NAME)
internal class HomeHandlerStoreImpl : HomeHandlerStore, BaseHandlerStore<State, Action, Event>()