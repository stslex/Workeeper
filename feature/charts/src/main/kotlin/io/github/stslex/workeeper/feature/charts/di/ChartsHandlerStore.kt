package io.github.stslex.workeeper.feature.charts.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.State

internal interface ChartsHandlerStore : HandlerStore<State, Action, Event>
