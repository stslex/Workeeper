package io.github.stslex.workeeper.feature.charts.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State
import javax.inject.Inject

@ViewModelScoped
internal class ChartsHandlerStoreImpl @Inject constructor() : ChartsHandlerStore,
    BaseHandlerStore<State, Action, Event>()
