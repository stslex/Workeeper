// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStoreImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsClickHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsComponent
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsInputHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsNavigationHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsPagingHandler
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State

@HiltViewModel(assistedFactory = SettingsStoreImpl.Factory::class)
internal class SettingsStoreImpl @AssistedInject constructor(
    @Assisted component: SettingsComponent,
    pagingHandler: SettingsPagingHandler,
    clickHandler: SettingsClickHandler,
    inputHandler: SettingsInputHandler,
    interactor: SettingsInteractor,
    storeDispatchers: StoreDispatchers,
    storeEmitter: SettingsHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.initial(
        appVersion = interactor.appVersionName(),
        appVersionCode = interactor.appVersionCode().toInt(),
    ),
    storeEmitter = storeEmitter,
    storeDispatchers = storeDispatchers,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> component as SettingsNavigationHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    initialActions = listOf(Action.Paging.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<SettingsComponent, SettingsStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "Settings"
    }
}
