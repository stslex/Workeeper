// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
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

internal interface SingleTrainingStore : Store<State, Action, Event> {

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
        val activeSession: ActiveSessionInfo?,
        val canPermanentlyDelete: Boolean,
        val originalSnapshot: Snapshot?,
        val planEditorTarget: PlanEditorTarget?,
        val pickerState: PickerState,
        val isLoading: Boolean,
    ) : Store.State {

        val isCreate: Boolean get() = (mode as? Mode.Edit)?.isCreate == true

        val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false

        val isPlanEditorDirty: Boolean
            get() = planEditorTarget?.let { it.draft != it.initialPlan } == true

        /**
         * Intercepting back is required when EITHER training-level edits OR plan-editor
         * draft changes are unsaved. The handler routes the back to the right discard
         * surface based on which is dirty.
         */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && hasChanges) || isPlanEditorDirty

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
        data class PlanEditorTarget(
            val exerciseUuid: String,
            val exerciseName: String,
            val exerciseType: ExerciseTypeUiModel,
            /** Snapshot of the plan when the editor opened — used for dirty detection. */
            val initialPlan: ImmutableList<PlanSetUiModel>,
            /** Live draft updated on every editor field change. */
            val draft: ImmutableList<PlanSetUiModel>,
        ) {

            val isWeighted: Boolean get() = exerciseType == ExerciseTypeUiModel.WEIGHTED
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
                planEditorTarget = null,
                pickerState = PickerState.Closed,
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

            data object OnEditClick : Click

            data object OnArchiveClick : Click

            data object OnPermanentDeleteClick : Click

            data object OnPermanentDeleteConfirm : Click

            data object OnPermanentDeleteDismiss : Click

            data object OnStartSessionClick : Click

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

        data class PlanEditAction(
            val action: AppPlanEditorAction,
        ) : Action

        sealed interface Navigation : Action {

            data object Back : Navigation

            data class OpenExerciseDetail(val uuid: String) : Navigation

            data class OpenSession(val sessionUuid: String) : Navigation

            data class OpenLiveWorkout(val sessionUuid: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val name: String) : Event

        data class ShowArchiveBlocked(val reason: String) : Event

        data object ShowDiscardConfirmDialog : Event

        data object ShowPermanentDeleteConfirmDialog : Event

        data object ShowLiveWorkoutPending : Event

        data class ShowOtherSessionActive(val trainingName: String) : Event

        data class ShowSaveError(val reason: String) : Event
    }

    companion object {

        const val SELECTED_TAGS_INITIAL_CAPACITY = 0
        val EMPTY_SELECTED: Set<String> = persistentSetOf()
    }
}
