// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStoreImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsBackupHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsClickHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsInputHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsNavigationHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsPagingHandler
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State

// Metro constructs this Store (class-level @Inject). Retention is
// owned by the Android ViewModelStore via rememberMetroStoreProcessor — so NO @SingleIn here.
@Inject
class SettingsStoreImpl internal constructor(
    navigationHandler: SettingsNavigationHandler,
    pagingHandler: SettingsPagingHandler,
    clickHandler: SettingsClickHandler,
    inputHandler: SettingsInputHandler,
    backupHandler: SettingsBackupHandler,
    interactor: SettingsInteractor,
    storeDispatchers: StoreDispatchers,
    storeEmitter: SettingsHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.initial(
        appVersion = interactor.appVersionName(),
        appVersionCode = interactor.appVersionCode().toInt(),
        backupAvailable = backupHandler.isAvailable,
    ),
    storeEmitter = storeEmitter,
    storeDispatchers = storeDispatchers,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> navigationHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.Backup -> backupHandler
        }
    },
    initialActions = buildList {
        add(Action.Paging.Init)
        if (backupHandler.isAvailable) {
            add(Action.Backup.ObserveAuth)
            add(Action.Backup.ObservePreferences)
            add(Action.Backup.ObserveRestoreState)
        }
    },
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
    appScopeLifetime = appScopeLifetime,
) {

    companion object {

        private const val NAME = "Settings"
    }
}
