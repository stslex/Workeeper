// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store

import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStoreImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler.AppDialogRepoHandler
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler.ChooseHandler
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State

/**
 * MVI Store backing `AppDialogHost`, routing [Action.RepoAction] to [AppDialogRepoHandler] and
 * [Action.Choose] to [ChooseHandler]; `initialActions` subscribes to the repository flow.
 */
// Plain Store — retention belongs to the ViewModelStore, so no @SingleIn here.
@Inject
class AppDialogStoreImpl internal constructor(
    repoHandler: AppDialogRepoHandler,
    chooseHandler: ChooseHandler,
    storeEmitter: AppDialogHandlerStoreImpl,
    storeDispatchers: StoreDispatchers,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.EMPTY,
    storeEmitter = storeEmitter,
    handlerCreator = { action ->
        when (action) {
            is Action.RepoAction -> repoHandler
            is Action.Choose -> chooseHandler
        }
    },
    initialActions = listOf(Action.RepoAction.Observe),
    storeDispatchers = storeDispatchers,
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
    appScopeLifetime = appScopeLifetime,
),
    AppDialogStore {

    companion object {
        private const val NAME = "AppDialog"
    }
}
