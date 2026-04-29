// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.mvi.mapper.TrainingListItemMapper.toUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import io.github.stslex.workeeper.feature.all_trainings.mvi.mapper.toUi as toTagUi

@ViewModelScoped
internal class PagingHandler @Inject constructor(
    private val interactor: AllTrainingsInteractor,
    private val resourceWrapper: ResourceWrapper,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    store: AllTrainingsHandlerStore,
) : Handler<Action.Paging>, AllTrainingsHandlerStore by store {

    val pagingUiState: PagingUiState<PagingData<TrainingListItemUi>> = PagingUiState {
        state.map { it.activeTagFilter }
            .distinctUntilChanged()
            .flatMapLatest { filter ->
                interactor.observeTrainings(filter)
                    .map { pagingData -> pagingData.map { it.toUi(resourceWrapper = resourceWrapper) } }
            }
            .flowOn(defaultDispatcher)
    }

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> observeAvailableTags()
        }
    }

    private fun observeAvailableTags() {
        interactor.observeAvailableTags().launch { tags ->
            updateStateImmediate { current ->
                current.copy(
                    availableTags = tags.map { it.toTagUi() }.toImmutableList(),
                )
            }
        }
    }
}
