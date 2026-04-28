// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal interface AllExercisesStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingUiState: PagingUiState<PagingData<ExerciseUiModel>>,
        val availableTags: ImmutableList<TagUiModel>,
        val activeTagFilter: ImmutableSet<String>,
        val pendingPermanentDelete: PendingDelete?,
        val selectionMode: SelectionMode,
        val pendingBulkDelete: PendingBulkDelete?,
    ) : Store.State {

        val isSelecting: Boolean get() = selectionMode is SelectionMode.On

        /**
         * Multi-select intercepts back so the gesture exits selection rather than the
         * tab. Outside selection mode the predictive-back preview keeps running for the
         * normal bottom-tab navigation.
         */
        val interceptBack: Boolean get() = isSelecting

        @Stable
        data class PendingDelete(
            val uuid: String,
            val name: String,
        )

        @Stable
        sealed interface SelectionMode {
            data object Off : SelectionMode

            @Stable
            data class On(
                val selectedUuids: ImmutableSet<String>,
                val canDeleteAll: Boolean,
            ) : SelectionMode
        }

        @Stable
        data class PendingBulkDelete(val count: Int)

        companion object {

            fun init(
                pagingUiState: PagingUiState<PagingData<ExerciseUiModel>>,
            ): State = State(
                pagingUiState = pagingUiState,
                availableTags = persistentListOf(),
                activeTagFilter = persistentSetOf(),
                pendingPermanentDelete = null,
                selectionMode = SelectionMode.Off,
                pendingBulkDelete = null,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Click : Action {

            data class OnExerciseClick(val uuid: String) : Click

            data class OnExerciseLongPress(val uuid: String) : Click

            data object OnFabClick : Click

            data class OnTagFilterToggle(val tagUuid: String) : Click

            data class OnArchiveSwipe(val uuid: String, val name: String) : Click

            data class OnUndoArchive(val uuid: String) : Click

            data object OnConfirmPermanentDelete : Click

            data object OnCancelPermanentDelete : Click

            data class OnSelectionToggle(val uuid: String) : Click

            data object OnSelectionExit : Click

            data object OnBulkArchive : Click

            data object OnBulkDelete : Click

            data object OnBulkDeleteConfirm : Click

            data object OnBulkDeleteDismiss : Click
        }

        sealed interface Navigation : Action {

            data class OpenDetail(val uuid: String) : Navigation

            data object OpenCreate : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val uuid: String, val message: String) : Event

        data class ShowArchiveBlocked(val message: String) : Event

        data class ShowPermanentDeleteSuccess(val message: String) : Event

        data class ShowBulkArchiveSuccess(val message: String) : Event

        data class ShowBulkArchiveBlocked(val message: String) : Event

        data class ShowBulkDeleteSuccess(val message: String) : Event
    }
}
