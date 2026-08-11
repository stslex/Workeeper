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
         * Whether the empty state may offer «Начать пустую тренировку».
         *
         * **The pair is contract (§26 "Empty state"), and this is the one condition that withdraws
         * half of it.** Not a deviation invented here: `HomeStore.showStartCta` is
         * `activeSession == null && !isLoading`, so the app already decided that a blank-start
         * affordance withdraws while a workout is running. This screen is the second door to the
         * same route and takes the same rule.
         *
         * Why it is load-bearing rather than tidy: the route passes two null UUIDs, which sends
         * `LiveWorkoutCommonHandler.createSession` down its blank branch to
         * `createAdhocSession` — an **unconditional** insert of a fresh ad-hoc training plus an
         * `IN_PROGRESS` session. With one already running, `SessionDao.observeActive()` is
         * `WHERE state = 'IN_PROGRESS' LIMIT 1` with no `ORDER BY`, so the app then follows an
         * arbitrary one of the two and the other is orphaned: unreachable and unfinishable.
         *
         * And the collision is invisible on this screen by construction, which is what made it
         * reachable — `pagedActiveWithStats` filters `is_adhoc = 0`, so a running ad-hoc workout
         * puts no row in this list, and the list reads as empty while a workout is in progress.
         *
         * B27 records the underlying hole, because the guard being here means every *future* entry
         * point has to remember it too.
         */
        val showStartBlank: Boolean get() = hasActiveSession.not()

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
                // Assumed running until the first emission says otherwise: the affordance this
                // gates creates a second session if it is wrong, so the safe default is the one
                // that withholds it for a frame, not the one that offers it.
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
             * [Action.Navigation.OpenBlankSession], the blank-init adhoc entry.
             */
            data object OnEmptyStartBlank : Click

            data class OnTagFilterToggle(val tagUuid: String) : Click

            /**
             * Clears the whole tag filter in one act.
             *
             * The filtered-to-empty state's only action. It clears rather than creates: a create
             * button under a filter the user has just used answers a question they did not ask,
             * and leaves the filter in place so the thing they create disappears on arrival.
             * Distinct from [OnTagFilterToggle] because untoggling N chips is N taps while the
             * state being recovered from is one condition, not N.
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
             * A session with no training behind it. `Screen.LiveWorkout`'s KDoc claims at least one
             * uuid must be non-null; that is stale — `blank-init adhoc entry leaves both uuids null
             * for downstream session creation` is a shipped, tested path, and it is the destination
             * the drawn «Начать пустую тренировку» asks for.
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
