// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesScope
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.toUi
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.toUi as toTagUi

@SingleIn(AllExercisesScope::class)
internal class PagingHandler @Inject constructor(
    private val interactor: AllExercisesInteractor,
    private val resourceWrapper: ResourceWrapper,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    store: AllExercisesHandlerStore,
) : Handler<Action.Paging>, AllExercisesHandlerStore by store {

    val pagingUiState: PagingUiState<PagingData<ExerciseUiModel>> = PagingUiState {
        state.map { it.activeTagFilter }
            .distinctUntilChanged()
            .flatMapLatest { filter ->
                interactor.observeExercises(filter)
                    .map { pagingData ->
                        pagingData.map { item ->
                            item.toUi(resourceWrapper = resourceWrapper)
                        }
                    }
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
