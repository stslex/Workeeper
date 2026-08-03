// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.R
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.ImmutableList

/**
 * Full-screen plan editor. Holds the draft set list, owns the WEIGHTED / WEIGHTLESS type,
 * and persists either to the DB ([Mode.Existing] / [Mode.PerformedExercise]) or back to the
 * caller as a [PlanDraftResult] payload ([Mode.Draft]).
 */
interface PlanEditorStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val mode: Mode,
        val isLoading: Boolean,
        val exerciseName: String,
        val type: ExerciseTypeUiModel,
        val initialDraft: ImmutableList<PlanSetUiModel>,
        val draft: ImmutableList<PlanSetUiModel>,
        val initialType: ExerciseTypeUiModel,
        val pendingTypeChange: ExerciseTypeUiModel?,
        val isSaving: Boolean,
        val dialogState: DialogState,
    ) : Store.State {

        /** True when the working type or draft differ from their initial values. */
        val isDirty: Boolean
            get() = draft != initialDraft || type != initialType

        val isWeighted: Boolean
            get() = type == ExerciseTypeUiModel.WEIGHTED

        /**
         * BackHandler intercepts when there are unsaved edits OR when a modal is open — EXCEPT
         * when the open modal is the discard sheet itself, which is the second press of the same
         * gesture and must reach nav so back means back.
         *
         * That exception used to be spelled `!confirmDiscardOpen`, a second channel beside
         * `dialogState` (§26). Collapsing the two makes the clause name the variant instead of a
         * parallel flag, which is the whole point: the state that must not intercept is now a
         * value of the same field the rest of the predicate reads.
         */
        val interceptBack: Boolean
            get() = (isDirty || dialogState !is DialogState.Hidden) &&
                dialogState !is DialogState.DiscardConfirm

        @Stable
        sealed interface Mode {

            /**
             * Editing the plan attached to either a performed-exercise row in a live session
             * (live workout — [performedExerciseUuid] non-null) or to a training template row
             * (single-training edit — [performedExerciseUuid] null, [trainingUuid] non-null).
             * The backing store is always `plan_sets` on `training_exercise_table` keyed by
             * `(trainingUuid, exerciseUuid)`. When [trainingUuid] is null the exercise is
             * ad-hoc (live workout adhoc session) and the editor falls back to
             * `last_adhoc_sets`.
             */
            @Stable
            data class PerformedExercise(
                val performedExerciseUuid: String?,
                val exerciseUuid: String,
                val trainingUuid: String?,
            ) : Mode

            /**
             * Editing the default plan attached to a persisted exercise (no live session, no
             * training association). The backing store is `exercise_table.last_adhoc_sets`
             * and `exercise_table.type`.
             */
            @Stable
            data class Exercise(val exerciseUuid: String) : Mode

            /**
             * Editing a fresh draft for an exercise that is still being created (no
             * persisted UUID yet). PlanEditor does not touch the DB; Save returns the draft
             * to the caller via [PlanDraftResult] JSON in
             * [io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.Companion.planEditorDraftResultAttr].
             */
            @Stable
            data object Draft : Mode
        }

        companion object {

            fun init(
                mode: Mode,
                seedType: ExerciseTypeUiModel,
                seedPlan: ImmutableList<PlanSetUiModel>,
            ): State = State(
                mode = mode,
                isLoading = mode !is Mode.Draft,
                exerciseName = "",
                type = seedType,
                initialDraft = seedPlan,
                draft = seedPlan,
                initialType = seedType,
                pendingTypeChange = null,
                isSaving = false,
                dialogState = DialogState.Hidden,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data object Init : Common
        }

        sealed interface Click : Action {

            data object OnAddSet : Click

            data class OnSetRemove(val index: Int) : Click

            data class OnSetTypeChange(val index: Int, val value: SetTypeUiModel) : Click

            data class OnTypeToggle(val target: ExerciseTypeUiModel) : Click

            data object OnTypeChangeConfirm : Click

            data object OnTypeChangeDismiss : Click

            data object OnSave : Click

            data object OnBackClick : Click

            data object OnConfirmDiscard : Click

            data object OnDismissDiscard : Click
        }

        sealed interface Input : Action {

            data class OnSetWeightChange(val index: Int, val value: Double?) : Input

            data class OnSetRepsChange(val index: Int, val value: Int) : Input
        }

        // todo need refactor action naming rules for mvi
        @Suppress("MviActionNamingRule")
        data class EditorAction(val action: PlanEditorBodyAction) : Action

        sealed interface Navigation : Action {

            data object Back : Navigation

            /**
             * Pop after a successful save in [Mode.Existing] / [Mode.PerformedExercise] /
             * [Mode.Exercise]. The NavigationHandler writes the
             * `plan-editor-saved` flag to the previous backstack entry's
             * SavedStateHandle so the caller (Live workout, Single training,
             * Exercise detail) reloads its plan-driven state on resume.
             */
            data object BackAfterSave : Navigation

            /**
             * Pop after Done in [Mode.Draft]. Carries the serialized
             * [io.github.stslex.workeeper.feature.plan_editor.ui.mvi.model.PlanDraftResult]
             * JSON for the caller to merge into its local state.
             */
            data class BackAfterDraftSave(val resultJson: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowError(val type: ErrorType) : Event
    }

    @Stable
    enum class ErrorType(val msgRes: Int) {
        LoadFailed(R.string.core_ui_plan_editor_error_load),
        SaveFailed(R.string.core_ui_plan_editor_error_save),
    }
}
