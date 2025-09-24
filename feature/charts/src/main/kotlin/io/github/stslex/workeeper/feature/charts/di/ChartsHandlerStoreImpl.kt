package io.github.stslex.workeeper.feature.charts.di

import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [ChartsHandlerStore::class, HandlerStoreEmitter::class])
@Scope(name = CHARTS_SCOPE_NAME)
@Named(CHARTS_SCOPE_NAME)
internal class ChartsHandlerStoreImpl : ChartsHandlerStore, BaseHandlerStore<State, Action, Event>()
