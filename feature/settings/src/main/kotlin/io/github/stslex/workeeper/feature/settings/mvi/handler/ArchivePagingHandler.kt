// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.mapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class ArchivePagingHandler @Inject constructor(
    private val interactor: SettingsInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: ArchiveHandlerStore,
) : Handler<Action.Paging>, ArchiveHandlerStore by store {

    val archivedExercisesPaging: PagingUiState<PagingData<ArchivedItemUi.Exercise>> = PagingUiState {
        interactor
            .pagedArchivedExercises()
            .map { paging -> paging.map { item -> item.toUi(resourceWrapper) } }
    }

    val archivedTrainingsPaging: PagingUiState<PagingData<ArchivedItemUi.Training>> = PagingUiState {
        interactor
            .pagedArchivedTrainings()
            .map { paging -> paging.map { item -> item.toUi(resourceWrapper) } }
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
