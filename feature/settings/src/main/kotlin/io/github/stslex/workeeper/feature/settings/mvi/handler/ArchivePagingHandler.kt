// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.paging.PagingData
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@ViewModelScoped
internal class ArchivePagingHandler @Inject constructor(
    private val interactor: SettingsInteractor,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    store: ArchiveHandlerStore,
) : Handler<Action.Paging>, ArchiveHandlerStore by store {

    val archivedExercisesPaging: PagingUiState<PagingData<ArchivedItem.Exercise>> = PagingUiState {
        interactor.pagedArchivedExercises().flowOn(defaultDispatcher)
    }

    val archivedTrainingsPaging: PagingUiState<PagingData<ArchivedItem.Training>> = PagingUiState {
        interactor.pagedArchivedTrainings().flowOn(defaultDispatcher)
    }

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> initObservers()
        }
    }

    private fun initObservers() {
        scope.launch(interactor.observeArchivedExerciseCount()) { count ->
            updateStateImmediate { it.copy(exerciseCount = count) }
        }
        scope.launch(interactor.observeArchivedTrainingCount()) { count ->
            updateStateImmediate { it.copy(trainingCount = count) }
        }
    }
}
