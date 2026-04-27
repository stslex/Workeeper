// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import javax.inject.Inject

@ViewModelScoped
internal class HomeHandlerStoreImpl @Inject constructor() : HomeHandlerStore,
    BaseHandlerStore<State, Action, Event>()
