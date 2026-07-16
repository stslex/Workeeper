// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStoreImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.handler.BackupClickHandler
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
internal class SettingsStoreImpl(
    navigationHandler: SettingsNavigationHandler,
    pagingHandler: SettingsPagingHandler,
    clickHandler: SettingsClickHandler,
    inputHandler: SettingsInputHandler,
    backupClickHandler: BackupClickHandler,
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
            is Action.Navigation -> navigationHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.Backup -> backupClickHandler
        }
    },
    initialActions = listOf(
        Action.Paging.Init,
        Action.Backup.ObserveAuth,
        Action.Backup.ObservePreferences,
        Action.Backup.ObserveRestoreState,
    ),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "Settings"
    }
}
