// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.archive.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.archive.di.ArchiveScope
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveMetaCopy
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveUiMapper.toUi
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.resources.Res
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_kind_exercise
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_kind_training
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_label_archived
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_label_archived_since_format
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_meta_separator
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_segment_exercises
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_segment_trainings
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString

/**
 * Archive copy resolves here, in suspending Flow and Paging work, so State only ever receives
 * plain strings. [ResourceWrapper] is kept for the locale-aware date alone.
 */
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
                .map { paging ->
                    val copy = metaCopy()
                    paging.map { item -> item.toUi(copy, archivedSince(item)) }
                }
        }

    val archivedTrainingsPaging: PagingUiState<PagingData<ArchivedItemUi.Training>> =
        PagingUiState {
            interactor
                .pagedArchivedTrainings()
                .map { paging ->
                    val copy = metaCopy()
                    paging.map { item -> item.toUi(copy, archivedSince(item)) }
                }
        }

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> initObservers()
        }
    }

    private suspend fun metaCopy(): ArchiveMetaCopy = ArchiveMetaCopy(
        exerciseKind = getString(Res.string.feature_archive_kind_exercise),
        trainingKind = getString(Res.string.feature_archive_kind_training),
        archived = getString(Res.string.feature_archive_label_archived),
        separator = getString(Res.string.feature_archive_meta_separator),
    )

    /** «в архиве с <date>» via [ResourceWrapper.formatDayMonth], never a relative span. */
    private suspend fun archivedSince(item: ArchivedItem): String = getString(
        Res.string.feature_archive_label_archived_since_format,
        resourceWrapper.formatDayMonth(item.archivedAt),
    )

    /**
     * «Упражнения (12)» — the segment's word and its count, resolved before the State copy: a
     * golden of a segmented control photographs whatever label it is handed.
     */
    private fun initObservers() {
        interactor.observeArchivedExerciseCount().launch { count ->
            val label = getString(Res.string.feature_archive_segment_exercises, count)
            updateState {
                it.copy(
                    exerciseCount = count,
                    exerciseSegmentLabel = label,
                )
            }
        }
        interactor.observeArchivedTrainingCount().launch { count ->
            val label = getString(Res.string.feature_archive_segment_trainings, count)
            updateState {
                it.copy(
                    trainingCount = count,
                    trainingSegmentLabel = label,
                )
            }
        }
    }
}
