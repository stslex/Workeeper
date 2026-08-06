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
        /**
         * Which edit session the current draft belongs to — bumped on every entry into
         * [Mode.Edit]. The draft-undo toasts carry it back with their action: a toast
         * outlives the draft it edited (5s, accessibility-stretched), so Save or Cancel can
         * end the draft — and Edit can start a new one — while «Отменить» is still on
         * screen. The undo handlers no-op unless the epoch still matches, so a stale undo
         * cannot put an unsaved row onto the Read screen or into a draft it never edited.
         */
        val draftEpoch: Int,
        /**
         * A save's write is in flight: the snapshot is already captured, so the draft may
         * not take an undo any more — a row restored now would reach the screen and miss
         * the database. Set when Save dispatches, cleared on every outcome (the success
         * flip to Read, or the failure that keeps the draft alive and re-arms its undos).
         */
        val isSaving: Boolean,
        val name: String,
        val nameError: Boolean,
        val description: String,
        val tags: ImmutableList<AppTagItem>,
        val availableTags: ImmutableList<AppTagItem>,
        val tagSearchQuery: String,
        val exercises: ImmutableList<TrainingExerciseItem>,
        /**
         * The open cards in the editor's exercise list — empty when all are collapsed, which
         * is the INITIAL state (ED14) and not a limit: **expansion is per card, never an
         * accordion**. An accordion trades scroll stability for height — collapsing a card
         * above the viewport shifts the page under the user's finger mid-edit — and height is
         * the cheaper of the two. The STORE owns this rather than the card because an insert
         * opens the inserted card (D-OPEN-8), and a `remember` could not see the insert.
         */
        val expandedExerciseUuids: ImmutableSet<String>,
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

        // NO SAVE-ENABLED PREDICATE HERE, and none may be added. On this screen it would hide
        // TWO error branches, not one: `name.isNotBlank()` is the condition that produces
        // `nameError`, and `exercises.isNotEmpty()` is the one that emits `Event.ShowSaveError`
        // (§26, "Save is never disabled"). The empty list stays a snackbar — there is no field
        // for it to sit under and the drawing draws no error surface for a section (§7.3).

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false

        /**
         * Intercept back when edits are unsaved or a dialog is open — and a plan edited in a
         * card counts, through the plan half of [Snapshot]'s comparison. Dialog dismissal
         * precedes screen pop so the back gesture closes the topmost dialog before propagating.
         */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && hasChanges) || dialogState !is DialogState.Hidden

        @Stable
        sealed interface Mode {

            data object Read : Mode

            data class Edit(val isCreate: Boolean) : Mode
        }

        /**
         * The loaded form, kept whole so that discarding can put it back whole.
         *
         * [exercises] holds the ITEMS and must keep holding them: a signature of them is enough
         * to DETECT a change and not enough to UNDO one. Reduce this field to a signature and
         * the discard has to rebuild the list from the current one, which can only remove what
         * the user added — never restore what they removed — and leaves `position` at the
         * edited value on a screen that renders it as `"${position + 1}."`.
         *
         * The signature [matches] compares is derived from these items, on both sides, so
         * widening the field does not widen dirty detection.
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
                 * The three fields an edit can touch. `planSets` is IN it since the plan became
                 * an inline edit (ED1): a plan edit with no baseline echo must read as
                 * `hasChanges`, or back would pop over an unsaved plan without the discard sheet
                 * — the protection D-OPEN-11 counts on. Name, type and tags are NOT: they belong
                 * to the exercise, are edited on its own screen, and a refresh of them arriving
                 * from there is not an unsaved edit to THIS training.
                 */
                fun TrainingExerciseItem.signature() =
                    Triple(exerciseUuid, position, planSets)
            }
        }

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

            /**
             * The expanded card's plan edit — `PlanEditorBody`'s action, addressed to one
             * exercise of the list and reduced in memory (ED1): nothing is persisted until
             * Save writes every plan alongside the training.
             */
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

        /**
         * `− подход` in an expanded card is a DRAFT edit (§4's table): nothing is persisted,
         * so the undo puts [set] back at [index] in [exerciseUuid]'s draft — no timer, no
         * deferred anything. Item-wise so queued toasts compose.
         */
        data class ShowSetRemovedUndo(
            val message: String,
            val exerciseUuid: String,
            val set: PlanSetUiModel,
            val index: Int,
            /** [State.draftEpoch] at removal — the undo applies only to the same draft. */
            val draftEpoch: Int,
        ) : Event

        /**
         * `✕` removes from THIS training only, unconfirmed (D-OPEN-11) — the undo snackbar is
         * the affordance ED11 pairs with that absence. A draft edit like the row above it:
         * the undo re-inserts [item] where it stood (and re-opens it if it was expanded),
         * and nothing was persisted in between.
         */
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
