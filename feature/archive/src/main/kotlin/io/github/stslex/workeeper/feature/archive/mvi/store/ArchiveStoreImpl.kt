// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.archive.di.ArchiveHandlerStoreImpl
import io.github.stslex.workeeper.feature.archive.mvi.handler.ArchiveClickHandler
import io.github.stslex.workeeper.feature.archive.mvi.handler.ArchiveNavigationHandler
import io.github.stslex.workeeper.feature.archive.mvi.handler.ArchivePagingHandler
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State

// Metro constructs this Store (class-level @Inject). Retention is owned by the Android ViewModelStore
// via rememberMetroStoreProcessor — so NO @SingleIn here. The class is `public` (its accessor is on the
// public extension), but the primary constructor is `internal` so the handler ctor params stay internal —
// :app's generated extension impl calls the ctor at the IR level (no Kotlin `internal` barrier).
@Inject
class ArchiveStoreImpl internal constructor(
    navigationHandler: ArchiveNavigationHandler,
    pagingHandler: ArchivePagingHandler,
    clickHandler: ArchiveClickHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ArchiveHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(
        archivedExercisesPaging = pagingHandler.archivedExercisesPaging,
        archivedTrainingsPaging = pagingHandler.archivedTrainingsPaging,
    ),
    storeEmitter = storeEmitter,
    storeDispatchers = storeDispatchers,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> navigationHandler
            is Action.Click -> clickHandler
        }
    },
    initialActions = listOf(Action.Paging.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "Archive"
    }
}
