// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.exercise.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface ExerciseStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val mode: Mode,
        val name: String,
        val nameError: Boolean,
        val nameDuplicateError: Boolean,
        val type: ExerciseTypeDataModel,
        val description: String,
        val tags: ImmutableList<TagUiModel>,
        val availableTags: ImmutableList<TagUiModel>,
        val tagSearchQuery: String,
        val recentHistory: ImmutableList<HistoryUiModel>,
        val originalSnapshot: Snapshot?,
        val isLoading: Boolean,
        val canPermanentlyDelete: Boolean,
        val adhocPlan: ImmutableList<PlanSetDataModel>?,
        val planEditorTarget: PlanEditorTarget?,
        val pendingTypeChange: ExerciseTypeDataModel?,
    ) : Store.State {

        val isSaveEnabled: Boolean
            get() = name.isNotBlank()

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false

        val isPlanEditorDirty: Boolean
            get() = planEditorTarget?.let { it.draft != it.initialPlan } == true

        /**
         * True only when the system back gesture must surface the discard-changes dialog,
         * or when Edit on an existing exercise must flip back to Read instead of popping.
         * When false, BackHandler stays unsubscribed so Compose nav handles the gesture
         * natively (including the Android 13+ predictive-back preview animation).
         */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && (hasChanges || !mode.isCreate)) || isPlanEditorDirty

        @Stable
        sealed interface Mode {

            data object Read : Mode

            data class Edit(val isCreate: Boolean) : Mode
        }

        @Stable
        data class Snapshot(
            val name: String,
            val type: ExerciseTypeDataModel,
            val description: String,
            val tagUuids: List<String>,
        ) {

            fun matches(state: State): Boolean = state.name == name &&
                state.type == type &&
                state.description == description &&
                state.tags.map { it.uuid } == tagUuids
        }

        @Stable
        data class PlanEditorTarget(
            /** Snapshot of the plan when the editor opened — used for dirty detection. */
            val initialPlan: ImmutableList<PlanSetDataModel>,
            /** Live draft updated on every editor field change. */
            val draft: ImmutableList<PlanSetDataModel>,
        )

        companion object {

            fun create(uuid: String?): State = State(
                uuid = uuid,
                mode = if (uuid == null) Mode.Edit(isCreate = true) else Mode.Read,
                name = "",
                nameError = false,
                nameDuplicateError = false,
                type = ExerciseTypeDataModel.WEIGHTED,
                description = "",
                tags = persistentListOf(),
                availableTags = persistentListOf(),
                tagSearchQuery = "",
                recentHistory = persistentListOf(),
                originalSnapshot = null,
                isLoading = uuid != null,
                canPermanentlyDelete = false,
                adhocPlan = null,
                planEditorTarget = null,
                pendingTypeChange = null,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data object Init : Common
        }

        sealed interface Click : Action {

            data object OnBackClick : Click

            data object OnEditClick : Click

            data object OnArchiveMenuClick : Click

            data object OnTrackNowClick : Click

            data class OnHistoryRowClick(val sessionUuid: String) : Click

            data object OnSaveClick : Click

            data object OnCancelClick : Click

            data class OnConfirmDiscard(val target: DiscardTarget) : Click

            data object OnDismissDiscard : Click

            data object OnDismissArchiveBlocked : Click

            data object FlipToReadMode : Click

            data object OnPermanentDeleteMenuClick : Click

            data object OnConfirmPermanentDelete : Click

            data object OnDismissPermanentDelete : Click

            data class OnUndoArchive(val uuid: String) : Click

            data class OnTypeSelect(val type: ExerciseTypeDataModel) : Click

            data object OnTypeChangeConfirm : Click

            data object OnTypeChangeDismiss : Click

            data object OnEditPlanClick : Click

            data class OnPlanEditorSetWeight(val index: Int, val value: Double?) : Click

            data class OnPlanEditorSetReps(val index: Int, val reps: Int) : Click

            data class OnPlanEditorSetType(val index: Int, val type: SetTypeDataModel) : Click

            data class OnPlanEditorRemoveSet(val index: Int) : Click

            data object OnPlanEditorAddSet : Click

            data object OnPlanEditorSave : Click

            data object OnPlanEditorDismiss : Click

            data class OnTagToggle(val tagUuid: String) : Click

            data class OnTagRemove(val tagUuid: String) : Click

            data class OnTagCreate(val name: String) : Click
        }

        sealed interface Input : Action {

            data class OnNameChange(val value: String) : Input

            data class OnDescriptionChange(val value: String) : Input

            data class OnTagSearchChange(val value: String) : Input
        }

        sealed interface Navigation : Action {

            data object Back : Navigation

            data class OpenSession(val sessionUuid: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val name: String, val uuid: String) : Event

        data class ShowArchiveBlocked(val exerciseName: String, val trainings: List<String>) : Event

        data object ShowTagLimitReached : Event

        data object ShowTrackNowPending : Event

        data class ShowDiscardConfirmDialog(val target: DiscardTarget) : Event

        data class ShowPermanentDeleteConfirm(val name: String) : Event

        data object ShowPermanentDeleteSuccess : Event

        data object ShowTypeChangeConfirm : Event
    }

    /**
     * Where the user is heading after confirming a discard. Lets the dialog reuse a single
     * surface for the form-level (POP_SCREEN/FLIP_TO_READ) and plan-editor (PLAN_EDITOR)
     * scopes — same dialog, different commit semantics.
     */
    @Stable
    enum class DiscardTarget { POP_SCREEN, FLIP_TO_READ, PLAN_EDITOR }
}
