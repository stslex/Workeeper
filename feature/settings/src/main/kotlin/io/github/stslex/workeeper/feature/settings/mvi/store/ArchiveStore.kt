// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.State

internal interface ArchiveStore : Store<State, Action, Event> {

    @Stable
    enum class Segment { EXERCISES, TRAININGS }

    @Stable
    data class State(
        val selectedSegment: Segment,
        val exerciseCount: Int,
        val trainingCount: Int,
        val archivedExercisesPaging: PagingUiState<PagingData<ArchivedItemUi.Exercise>>,
        val archivedTrainingsPaging: PagingUiState<PagingData<ArchivedItemUi.Training>>,
        val pendingDeleteImpact: Int?,
        val pendingDeleteTarget: ArchivedItem?,
        val deleteImpactLoading: Boolean,
    ) : Store.State {

        companion object {

            fun init(
                archivedExercisesPaging: PagingUiState<PagingData<ArchivedItemUi.Exercise>>,
                archivedTrainingsPaging: PagingUiState<PagingData<ArchivedItemUi.Training>>,
            ): State = State(
                selectedSegment = Segment.EXERCISES,
                exerciseCount = 0,
                trainingCount = 0,
                archivedExercisesPaging = archivedExercisesPaging,
                archivedTrainingsPaging = archivedTrainingsPaging,
                pendingDeleteImpact = null,
                pendingDeleteTarget = null,
                deleteImpactLoading = false,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Click : Action {

            data class OnSegmentChange(val segment: Segment) : Click

            data class OnRestoreClick(val item: ArchivedItem) : Click

            data class OnPermanentDeleteClick(val item: ArchivedItem) : Click

            data object OnDeleteConfirm : Click

            data object OnDeleteDismiss : Click

            data class OnUndoRestore(val item: ArchivedItem) : Click
        }

        sealed interface Navigation : Action {

            data object Back : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class ShowRestoredSnackbar(val item: ArchivedItem) : Event

        data class ShowPermanentlyDeletedSnackbar(val name: String) : Event

        data class Haptic(val type: HapticFeedbackType) : Event
    }
}
