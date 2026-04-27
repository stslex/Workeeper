// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal interface AllTrainingsStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingUiState: PagingUiState<PagingData<TrainingListItemUi>>,
        val availableTags: ImmutableList<TagUiModel>,
        val activeTagFilter: ImmutableSet<String>,
        val selectionMode: SelectionMode,
        val pendingBulkDelete: PendingBulkDelete?,
    ) : Store.State {

        val isSelecting: Boolean get() = selectionMode is SelectionMode.On

        /**
         * BackHandler intercepts the gesture only when selection mode is on, so the
         * Android 13+ predictive-back preview keeps running for the normal tab navigation
         * case. Spec §"Multi-select mode" requires back to exit selection rather than the
         * screen.
         */
        val interceptBack: Boolean get() = isSelecting

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
                pagingUiState: PagingUiState<PagingData<TrainingListItemUi>>,
            ): State = State(
                pagingUiState = pagingUiState,
                availableTags = persistentListOf(),
                activeTagFilter = persistentSetOf(),
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

            data class OnTrainingClick(val uuid: String) : Click

            data class OnTrainingLongPress(val uuid: String) : Click

            data object OnFabClick : Click

            data class OnTagFilterToggle(val tagUuid: String) : Click

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

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowBulkArchiveSuccess(val count: Int) : Event

        data class ShowBulkArchiveBlocked(
            val archivedCount: Int,
            val blockedNames: List<String>,
        ) : Event

        data class ShowBulkDeleteSuccess(val count: Int) : Event
    }
}
