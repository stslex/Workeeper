// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

interface PastSessionStore :
    Store<PastSessionStore.State, PastSessionStore.Action, PastSessionStore.Event> {

    @Stable
    data class State(
        val sessionUuid: String,
        val phase: Phase,
        /**
         * The open cards — the whole disclosure model, by decision (spec §7 as amended by
         * the session rebuild): expanded means open, nothing more. The first Loaded emission
         * opens the FIRST card; a header tap flips exactly this set's membership for that
         * card; nothing else ever writes it — no auto-advance, no auto-collapse, no
         * "exactly one open". Multiple open cards are legal and expected.
         *
         * Store-homed per the rebuild contract, superseding the unmerged v3-screens
         * rework's `rememberSaveable` — the same home `feature/live-workout` uses
         * (`LiveWorkoutStore.State.expandedExerciseUuids`), so the two session surfaces
         * cannot drift on where disclosure lives.
         */
        val expandedExerciseUuids: ImmutableSet<String>,
        val deleteDialogVisible: Boolean,
    ) : Store.State {

        @Stable
        sealed interface Phase {
            @Stable
            data object Loading : Phase

            @Stable
            data class Loaded(val detail: PastSessionUiModel) : Phase

            @Stable
            data class Error(val errorType: ErrorType) : Phase
        }

        val canDelete: Boolean get() = phase is Phase.Loaded

        companion object {

            fun create(sessionUuid: String): State = State(
                sessionUuid = sessionUuid,
                phase = Phase.Loading,
                expandedExerciseUuids = persistentSetOf(),
                deleteDialogVisible = false,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {
            data object OnBackClick : Click
            data object OnDeleteClick : Click
            data object OnDeleteConfirm : Click
            data object OnDeleteDismiss : Click
            data class OnSetTypeChange(
                val setUuid: String,
                val type: SetTypeUiModel,
            ) : Click

            /**
             * Reorder request from the structural-edit drag gesture (v2.4 5.7).
             * [from] / [to] are positional indices within [performedExerciseUuid]'s set
             * list as displayed at drag-start time.
             */
            data class OnSetReorder(
                val performedExerciseUuid: String,
                val from: Int,
                val to: Int,
            ) : Click

            data object OnDragStarted : Click

            data object OnRetryLoad : Click

            /**
             * A card-header tap. Flips [performedExerciseUuid]'s membership in
             * [State.expandedExerciseUuids] and does nothing else anywhere — the complete
             * toggle rule of the amended §7 disclosure contract.
             */
            data class OnExerciseHeaderClick(val performedExerciseUuid: String) : Click
        }

        sealed interface Input : Action {
            data class OnSetWeightChange(val setUuid: String, val raw: String) : Input
            data class OnSetRepsChange(val setUuid: String, val raw: String) : Input
        }

        sealed interface Navigation : Action {
            data object Back : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowError(val errorType: ErrorType) : Event
        data object DeletedSnackbar : Event
        data object SaveFailedSnackbar : Event
    }
}
