// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStoreImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler.ChooseHandler
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler.DismissHandler
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler.ObserveHandler
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler.PublishHandler
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import javax.inject.Inject

/**
 * The genuine MVI Store backing `AppDialogHost`. Routes each `Action` variant
 * to its `@ViewModelScoped` handler and lives at the host Activity's
 * `ViewModelStore` because it is obtained at the App root via
 * `AppDialogFeature` (a screen-less `AppFeature` composition entry).
 *
 * The standard `Store → @HiltViewModel` predicate routes this class without
 * a carve-out — `documentation/lint-rules.md → HiltScopeRule` covers the
 * naming → scope mapping.
 *
 * `initialActions = listOf(Action.Observe)` subscribes to the repository
 * flow once at Store init. Subsequent re-subscription on Activity recreation
 * is automatic: a fresh `AppDialogStoreImpl` is constructed against the
 * recreated Activity's `ViewModelStore` and re-runs its initial actions.
 */
@HiltViewModel
internal class AppDialogStoreImpl @Inject constructor(
    observeHandler: ObserveHandler,
    publishHandler: PublishHandler,
    dismissHandler: DismissHandler,
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
            Action.Observe -> observeHandler
            is Action.Publish -> publishHandler
            is Action.Dismiss -> dismissHandler
            is Action.Choose -> chooseHandler
        }
    },
    initialActions = listOf(Action.Observe),
    storeDispatchers = storeDispatchers,
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
),
    AppDialogStore {

    companion object {
        private const val NAME = "AppDialog"
    }
}
