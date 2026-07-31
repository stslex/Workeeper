// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.archive.di.ArchiveScope
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveUiMapper.toUi
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import kotlinx.coroutines.flow.map

@SingleIn(ArchiveScope::class)
internal class ArchivePagingHandler @Inject constructor(
    private val interactor: ArchiveInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: ArchiveHandlerStore,
) : Handler<Action.Paging>, ArchiveHandlerStore by store {

    val archivedExercisesPaging: PagingUiState<PagingData<ArchivedItemUi.Exercise>> =
        PagingUiState {
            interactor
                .pagedArchivedExercises()
                .map { paging -> paging.map { item -> item.toUi(resourceWrapper) } }
        }

    val archivedTrainingsPaging: PagingUiState<PagingData<ArchivedItemUi.Training>> =
        PagingUiState {
            interactor
                .pagedArchivedTrainings()
                .map { paging -> paging.map { item -> item.toUi(resourceWrapper) } }
        }

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> initObservers()
        }
    }

    /**
     * «Упражнения (12)» — the segment's word and its count, joined here rather than at the call
     * site. Named so `ArchiveSegmentLabelTest` can assert the composition: a golden of a segmented
     * control photographs whatever number it is handed and cannot say whether it is the right one.
     */
    private fun segmentLabel(labelRes: Int, count: Int): String =
        resourceWrapper.getString(labelRes, count)

    private fun initObservers() {
        interactor.observeArchivedExerciseCount().launch { count ->
            updateState {
                it.copy(
                    exerciseCount = count,
                    exerciseSegmentLabel = segmentLabel(
                        R.string.feature_archive_segment_exercises,
                        count,
                    ),
                )
            }
        }
        interactor.observeArchivedTrainingCount().launch { count ->
            updateState {
                it.copy(
                    trainingCount = count,
                    trainingSegmentLabel = segmentLabel(
                        R.string.feature_archive_segment_trainings,
                        count,
                    ),
                )
            }
        }
    }
}
