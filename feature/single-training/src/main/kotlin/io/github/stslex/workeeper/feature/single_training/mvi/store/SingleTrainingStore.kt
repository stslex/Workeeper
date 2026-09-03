// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

interface SingleTrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val mode: Mode,
        /** Edit-session id, bumped on every [Mode.Edit] entry; a stale undo toast no-ops. */
        val draftEpoch: Int,
        /** A save's write is in flight: the draft refuses both undo and discard until it lands. */
        val isSaving: Boolean,
        val name: String,
        val nameError: Boolean,
        val description: String,
        val tags: ImmutableList<AppTagItem>,
        val availableTags: ImmutableList<AppTagItem>,
        val tagSearchQuery: String,
        val exercises: ImmutableList<TrainingExerciseItem>,
        /**
         * Open cards in the editor list — per card, never an accordion (ED14). Store-owned
         * because a picker insert opens the inserted card (D-OPEN-8).
         */
        val expandedExerciseUuids: ImmutableSet<String>,
        /**
         * Set restores that landed while their card was absent — both removals queue toasts.
         * The exercise undo replays them in tap order when the card comes back.
         */
        val pendingSetRestores: ImmutableList<PendingSetRestore>,
        val pastSessions: ImmutableList<HistorySessionItem>,
        /** Total finished sessions of this training — the История head's count (§3.3). */
        val historyCount: Int,
        val activeSession: ActiveSessionDomain?,
        val canPermanentlyDelete: Boolean,
        val originalSnapshot: Snapshot?,
        val pickerState: PickerState,
        val dialogState: DialogState,
        val isLoading: Boolean,
    ) : Store.State {

        val isCreate: Boolean get() = (mode as? Mode.Edit)?.isCreate == true

        // GUARD: no save-enabled predicate here — it would hide both `nameError` and
        // `Event.ShowSaveError` (§26, "Save is never disabled").

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false

        /** Intercept back for an open dialog or unsaved edits (a card's plan edit counts). */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && hasChanges) || dialogState !is DialogState.Hidden

        @Stable
        sealed interface Mode {

            data object Read : Mode

            data class Edit(val isCreate: Boolean) : Mode
        }

        /**
         * The loaded form, kept whole so discard can put it back whole — [exercises] holds the
         * items, since a signature can detect a change but not undo one.
         */
        @Stable
        data class Snapshot(
            val name: String,
            val description: String,
            val tagUuids: List<String>,
            val exercises: ImmutableList<TrainingExerciseItem>,
        ) {

            fun matches(state: State): Boolean = state.name == name &&
                state.description == description &&
                state.tags.map { it.uuid } == tagUuids &&
                state.exercises.map { it.signature() } == exercises.map { it.signature() }

            private companion object {

                /**
                 * The three fields an edit can touch. `planSets` is in since plans became an
                 * inline edit (ED1); name, type and tags belong to the exercise, not here.
                 */
                fun TrainingExerciseItem.signature() =
                    Triple(exerciseUuid, position, planSets)
            }
        }

        /** One stashed set restore — see [State.pendingSetRestores]. */
        @Stable
        data class PendingSetRestore(
            val exerciseUuid: String,
            val set: PlanSetUiModel,
            val index: Int,
        )

        @Stable
        sealed interface PickerState {

            data object Closed : PickerState

            @Stable
            data class Open(
                val query: String,
                val results: ImmutableList<PickerExerciseItem>,
                val selectedUuids: ImmutableList<String>,
            ) : PickerState
        }

        companion object {

            fun create(uuid: String?): State = State(
                uuid = uuid,
                mode = if (uuid == null) Mode.Edit(isCreate = true) else Mode.Read,
                draftEpoch = 0,
                isSaving = false,
                name = "",
                nameError = false,
                description = "",
                tags = persistentListOf(),
                availableTags = persistentListOf(),
                tagSearchQuery = "",
                exercises = persistentListOf(),
                expandedExerciseUuids = persistentSetOf(),
                pendingSetRestores = persistentListOf(),
                pastSessions = persistentListOf(),
                historyCount = 0,
                activeSession = null,
                canPermanentlyDelete = false,
                originalSnapshot = null,
                pickerState = PickerState.Closed,
                dialogState = DialogState.Hidden,
                isLoading = uuid != null,
            )
        }
    }

    @Suppress("ComplexInterface")
    @Stable
    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data object Init : Common
        }

        sealed interface Click : Action {

            // Top-bar / detail clicks
            data object OnBackClick : Click

            /** Topbar `⋮` — opens the [DialogState.DetailMenu] sheet (ED10). */
            data object OnDetailMenuClick : Click

            data object OnDetailMenuDismiss : Click

            data object OnEditClick : Click

            data object OnArchiveClick : Click

            data object OnPermanentDeleteClick : Click

            data object OnPermanentDeleteConfirm : Click

            data object OnPermanentDeleteDismiss : Click

            data object OnStartSessionClick : Click

            data object OnConflictResume : Click

            data object OnConflictDeleteAndStart : Click

            data object OnConflictDismiss : Click

            data class OnExerciseRowClick(val exerciseUuid: String) : Click

            data class OnPastSessionClick(val sessionUuid: String) : Click

            // Edit-mode clicks
            data object OnSaveClick : Click

            data object OnCancelClick : Click

            data object OnConfirmDiscard : Click

            data object OnDismissDiscard : Click

            data object OnAddExerciseClick : Click

            data class OnExerciseRemove(val exerciseUuid: String) : Click

            /** «Отменить» on the exercise-removed toast: the draft takes [item] back. */
            data class OnUndoExerciseRemove(
                val item: TrainingExerciseItem,
                val wasExpanded: Boolean,
                val draftEpoch: Int,
            ) : Click

            /** «Отменить» on the set-removed toast: [set] back at [index] in one card's draft. */
            data class OnUndoSetRemove(
                val exerciseUuid: String,
                val set: PlanSetUiModel,
                val index: Int,
                val draftEpoch: Int,
            ) : Click

            data class OnExerciseReorder(val from: Int, val to: Int) : Click

            /** The card head's tap (ED14): expand the one you mean, collapse the one open. */
            data class OnExerciseCardToggle(val exerciseUuid: String) : Click

            /** One card's plan edit, reduced in memory (ED1); nothing persists until Save. */
            @Suppress("MviActionNamingRule")
            data class OnExercisePlanAction(
                val exerciseUuid: String,
                val action: PlanEditorBodyAction,
            ) : Click

            /** The form's dashed «+ тег» chip — opens the [DialogState.TagPicker] sheet. */
            data object OnTagAddClick : Click

            /** «Готово», the scrim or the drag — selection already applied live (ED7). */
            data object OnTagPickerDismiss : Click

            data class OnTagToggle(val tagUuid: String) : Click

            data class OnTagRemove(val tagUuid: String) : Click

            data class OnTagCreate(val name: String) : Click

            // Exercise picker
            data object OnPickerDismiss : Click

            data class OnPickerToggle(val uuid: String) : Click

            data object OnPickerConfirm : Click
        }

        sealed interface Input : Action {

            data class OnNameChange(val value: String) : Input

            data class OnDescriptionChange(val value: String) : Input

            data class OnTagSearchChange(val value: String) : Input

            data class OnPickerSearchChange(val value: String) : Input
        }

        sealed interface Navigation : Action {

            data object Back : Navigation

            data class OpenExerciseDetail(val uuid: String) : Navigation

            data class OpenSession(val sessionUuid: String) : Navigation

            data class OpenLiveWorkout(
                val sessionUuid: String,
                val trainingUuid: String?,
            ) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val message: String) : Event

        data class ShowArchiveBlocked(val message: String) : Event

        data class ShowSaveError(val message: String) : Event

        /** `− подход` is a draft edit: the undo puts [set] back at [index] in [exerciseUuid]. */
        data class ShowSetRemovedUndo(
            val message: String,
            val exerciseUuid: String,
            val set: PlanSetUiModel,
            val index: Int,
            /** [State.draftEpoch] at removal — the undo applies only to the same draft. */
            val draftEpoch: Int,
        ) : Event

        /** `✕` removes from this training only (D-OPEN-11); the undo re-inserts [item]. */
        data class ShowExerciseRemovedUndo(
            val message: String,
            val item: TrainingExerciseItem,
            val wasExpanded: Boolean,
            /** [State.draftEpoch] at removal — the undo applies only to the same draft. */
            val draftEpoch: Int,
        ) : Event
    }

    companion object {

        const val SELECTED_TAGS_INITIAL_CAPACITY = 0
        val EMPTY_SELECTED: Set<String> = persistentSetOf()
    }
}
