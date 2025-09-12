package io.github.stslex.workeeper.feature.home.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State

internal interface HomeHandlerStore : HandlerStore<State, Action, Event>
