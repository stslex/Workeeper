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
 * Full-screen plan editor. Holds the draft set list, owns the WEIGHTED / WEIGHTLESS type for
 * [Mode.Exercise], and persists to the DB on its own Save.
 *
 * **Creation does not come through here.** An exercise that has no persisted UUID is built on the
 * exercise form, which hosts [PlanEditorBody] inline — one screen, no route hop, no payload
 * handed back. This store serves the two modes that edit something already on disk.
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
         * The route's BackHandler intercepts when there are unsaved edits or a modal is open.
         *
         * **NO PER-VARIANT EXCEPTION HERE, and one must not be added to route a back press past
         * an open sheet.** Every modal on this screen is an `AppConfirmSheet`, i.e. a
         * `ModalBottomSheet`, and that renders in its own `ComponentDialog` window which consumes
         * system back itself and calls `onDismissRequest` (§26, "Every modal on the three editors
         * is a SHEET"). A back press with a sheet up therefore never reaches this route at all,
         * whatever this property says — so an exception here buys nothing and only describes a
         * flow that cannot happen.
         */
        val interceptBack: Boolean
            get() = isDirty || dialogState !is DialogState.Hidden

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
        }

        companion object {

            fun init(
                mode: Mode,
                seedType: ExerciseTypeUiModel,
                seedPlan: ImmutableList<PlanSetUiModel>,
            ): State = State(
                mode = mode,
                isLoading = true,
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
             * Pop after a successful save in [Mode.Exercise] / [Mode.PerformedExercise].
             * The NavigationHandler writes the
             * `plan-editor-saved` flag to the previous backstack entry's
             * SavedStateHandle so the caller (Live workout, Single training,
             * Exercise detail) reloads its plan-driven state on resume.
             */
            data object BackAfterSave : Navigation
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
