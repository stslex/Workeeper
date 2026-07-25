// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store

import dev.zacsweers.metro.Inject
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
 * The genuine MVI Store backing `AppDialogHost`. Routes repository-touching
 * actions ([Action.RepoAction] sub-tree) to [AppDialogRepoHandler] and
 * user-choice actions to [ChooseHandler]. Lives at the host Activity's
 * `ViewModelStore` because it is obtained at the App root via
 * `AppDialogFeature` (a screen-less `AppFeature` composition entry).
 *
 * `initialActions = listOf(Action.RepoAction.Observe)` subscribes to the
 * repository flow once at Store init. Subsequent re-subscription on Activity
 * recreation is automatic: a fresh `AppDialogStoreImpl` is constructed
 * against the recreated Activity's `ViewModelStore` and re-runs its initial
 * actions.
 */
// Metro constructs this PLAIN Store (class-level @Inject). Retention is
// owned by the Activity's ViewModelStore via rememberMetroStoreProcessor (root-mounted through
// AppFeature) — so NO @SingleIn here.
@Inject
class AppDialogStoreImpl internal constructor(
    repoHandler: AppDialogRepoHandler,
    chooseHandler: ChooseHandler,
    storeEmitter: AppDialogHandlerStoreImpl,
    storeDispatchers: StoreDispatchers,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
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
),
    AppDialogStore {

    companion object {
        private const val NAME = "AppDialog"
    }
}
