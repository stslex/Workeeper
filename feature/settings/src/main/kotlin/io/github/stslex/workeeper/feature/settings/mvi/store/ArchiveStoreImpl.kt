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
import io.github.stslex.workeeper.feature.settings.di.ArchiveHandlerStoreImpl
import io.github.stslex.workeeper.feature.settings.mvi.handler.ArchiveClickHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.ArchiveComponent
import io.github.stslex.workeeper.feature.settings.mvi.handler.ArchiveNavigationHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.ArchivePagingHandler
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.State

@HiltViewModel(assistedFactory = ArchiveStoreImpl.Factory::class)
internal class ArchiveStoreImpl @AssistedInject constructor(
    @Assisted component: ArchiveComponent,
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
            is Action.Navigation -> component as ArchiveNavigationHandler
            is Action.Click -> clickHandler
        }
    },
    initialActions = listOf(Action.Paging.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<ArchiveComponent, ArchiveStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "Archive"
    }
}
