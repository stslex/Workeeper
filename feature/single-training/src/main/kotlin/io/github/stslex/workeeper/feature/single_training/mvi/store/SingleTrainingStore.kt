// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

interface SingleTrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val mode: Mode,
        val name: String,
        val nameError: Boolean,
        val description: String,
        val tags: ImmutableList<TagUiModel>,
        val availableTags: ImmutableList<TagUiModel>,
        val tagSearchQuery: String,
        val exercises: ImmutableList<TrainingExerciseItem>,
        val pastSessions: ImmutableList<HistorySessionItem>,
        val activeSession: ActiveSessionDomain?,
        val canPermanentlyDelete: Boolean,
        val originalSnapshot: Snapshot?,
        val pickerState: PickerState,
        val dialogState: DialogState,
        val isLoading: Boolean,
    ) : Store.State {

        val isCreate: Boolean get() = (mode as? Mode.Edit)?.isCreate == true

        // `canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()` USED TO LIVE
        // HERE, and it hid TWO branches rather than one (§26, "Save is never disabled"). The
        // first conjunct is the exact condition that produces `nameError`; the second is the
        // condition that emits `Event.ShowSaveError`. Both were unreachable from the UI and both
        // had a green `ClickHandlerTest` case certifying a state production could not enter.
        // Save is enabled always: a blank name is a field error the user can be pointed at, and
        // an empty exercise list stays a snackbar because there is no field for it to sit under
        // and the drawing draws no error surface for a section (extraction §7.3).

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false

        /**
         * Intercept back when training-level edits are unsaved or a dialog is open.
         * Plan-editor draft changes live on the standalone PlanEditor route now, so its
         * dirty-state is owned there. Dialog dismissal precedes screen pop so the back
         * gesture closes the topmost dialog before propagating.
         */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && hasChanges) || dialogState !is DialogState.Hidden

        @Stable
        sealed interface Mode {

            data object Read : Mode

            data class Edit(val isCreate: Boolean) : Mode
        }

        @Stable
        data class Snapshot(
            val name: String,
            val description: String,
            val tagUuids: List<String>,
            val exerciseSignature: List<ExerciseSignature>,
        ) {

            fun matches(state: State): Boolean = state.name == name &&
                state.description == description &&
                state.tags.map { it.uuid } == tagUuids &&
                state.exercises.map {
                    ExerciseSignature(
                        it.exerciseUuid,
                        it.position,
                    )
                } == exerciseSignature
        }

        @Stable
        data class ExerciseSignature(val exerciseUuid: String, val position: Int)

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
                name = "",
                nameError = false,
                description = "",
                tags = persistentListOf(),
                availableTags = persistentListOf(),
                tagSearchQuery = "",
                exercises = persistentListOf(),
                pastSessions = persistentListOf(),
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

            /**
             * Reload the training + per-exercise plans from the repository without
             * resetting form state. Dispatched after returning from the full-screen
             * PlanEditor route (D1) so the exercise list reflects the just-saved draft.
             */
            data object Reload : Common
        }

        sealed interface Click : Action {

            // Top-bar / detail clicks
            data object OnBackClick : Click

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

            data class OnExerciseReorder(val from: Int, val to: Int) : Click

            data class OnEditPlanClick(val exerciseUuid: String) : Click

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

            /**
             * Open the full-screen plan-editor route for the given (training, exercise)
             * pair (D1). Returns to SingleTraining; the graph picks up the
             * `plan-editor-saved` flag and dispatches [Action.Common.Reload].
             */
            data class OpenPlanEditor(
                val trainingUuid: String,
                val exerciseUuid: String,
            ) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val message: String) : Event

        data class ShowArchiveBlocked(val message: String) : Event

        data class ShowSaveError(val message: String) : Event
    }

    companion object {

        const val SELECTED_TAGS_INITIAL_CAPACITY = 0
        val EMPTY_SELECTED: Set<String> = persistentSetOf()
    }
}
