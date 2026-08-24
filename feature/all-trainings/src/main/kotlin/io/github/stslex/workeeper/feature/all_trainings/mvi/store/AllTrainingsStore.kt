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

interface AllTrainingsStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingUiState: PagingUiState<PagingData<TrainingListItemUi>>,
        val availableTags: ImmutableList<TagUiModel>,
        val activeTagFilter: ImmutableSet<String>,
        val selectionMode: SelectionMode,
        val pendingBulkDelete: PendingBulkDelete?,
        val hasActiveSession: Boolean,
    ) : Store.State {

        val isSelecting: Boolean get() = selectionMode is SelectionMode.On

        /**
         * Whether the empty state may offer «Начать пустую тренировку». Withdrawn while a session
         * runs — the blank route inserts unconditionally, so a second `IN_PROGRESS` orphans one.
         */
        val showStartBlank: Boolean get() = hasActiveSession.not()

        /**
         * Back exits selection rather than the screen. Off otherwise, so the Android 13+
         * predictive-back preview keeps running for tab navigation.
         */
        val interceptBack: Boolean get() = isSelecting

        @Stable
        sealed interface SelectionMode {
            data object Off : SelectionMode

            @Stable
            data class On(
                val selectedUuids: ImmutableSet<String>,
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
                // Assumed running until the first emission says otherwise: a wrong `false` here
                // offers an affordance that creates a second session.
                hasActiveSession = true,
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

            /** The empty state's primary CTA — «Создать тренировку». */
            data object OnEmptyCreate : Click

            /**
             * The empty state's secondary CTA — «Начать пустую тренировку». Reaches
             * [Action.Navigation.OpenBlankSession].
             */
            data object OnEmptyStartBlank : Click

            data class OnTagFilterToggle(val tagUuid: String) : Click

            /**
             * Clears the whole tag filter in one act — the filtered-to-empty state's only action.
             * Distinct from [OnTagFilterToggle]: untoggling N chips is N taps.
             */
            data object OnClearTagFilter : Click

            data class OnSelectionToggle(val uuid: String) : Click

            data object OnSelectionExit : Click

            data object OnBulkDeleteConfirm : Click

            data object OnBulkDeleteDismiss : Click
        }

        sealed interface Navigation : Action {

            data class OpenDetail(val uuid: String) : Navigation

            data object OpenCreate : Navigation

            /**
             * A session with no training behind it: both of `Screen.LiveWorkout`'s uuids stay
             * null and the session is created downstream.
             */
            data object OpenBlankSession : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowBulkDeleteSuccess(val message: String) : Event
    }
}
