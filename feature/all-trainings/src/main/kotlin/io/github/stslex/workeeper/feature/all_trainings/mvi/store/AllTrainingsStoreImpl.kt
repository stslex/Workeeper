// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State

// Metro constructs this PLAIN Store (class-level @Inject). Retention is owned by the Android
// ViewModelStore via rememberMetroStoreProcessor — so NO @SingleIn here. The class is `public` (its
// accessor is on the public extension), but the primary constructor is `internal` so the handler ctor
// params stay internal — :app's generated extension impl calls the ctor at the IR level (no Kotlin
// `internal` barrier). @Inject stays on the class (not the ctor) so the store keeps the class-level
// @Inject convention MetroScopeRule relies on.
@Inject
class AllTrainingsStoreImpl internal constructor(
    navigationHandler: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: AllTrainingsHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(pagingUiState = pagingHandler.pagingUiState),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Paging -> pagingHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Paging.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
    appScopeLifetime = appScopeLifetime,
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "AllTrainings"
    }
}
