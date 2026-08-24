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
 * Full-screen editor for a plan already on disk: holds the draft set list, owns the type for
 * [Mode.Exercise], and persists on its own Save. Creation never routes here.
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

        val isDirty: Boolean
            get() = draft != initialDraft || type != initialType

        val isWeighted: Boolean
            get() = type == ExerciseTypeUiModel.WEIGHTED

        /**
         * True when the route's BackHandler intercepts: unsaved edits or an open modal. No
         * per-variant exception — sheets own system back themselves, so one would be unreachable.
         */
        val interceptBack: Boolean
            get() = isDirty || dialogState !is DialogState.Hidden

        @Stable
        sealed interface Mode {

            /**
             * Editing a plan reached through a live session or a training row. Storage is keyed on
             * [trainingUuid]: null falls back to `last_adhoc_sets`. See architecture.md.
             */
            @Stable
            data class PerformedExercise(
                val performedExerciseUuid: String?,
                val exerciseUuid: String,
                val trainingUuid: String?,
            ) : Mode

            /** Editing a persisted exercise's own default plan: `last_adhoc_sets` + `type`. */
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
             * Pop after a successful save; the NavigationHandler flags the caller's backstack
             * entry so it reloads its plan-driven state on resume.
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
