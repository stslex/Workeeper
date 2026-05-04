// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Full-screen plan editor (v2.4 D1). Replaces the bottom-sheet `AppPlanEditor`. Holds the
 * draft set list, loads initial state from the repository on init, persists on save, and
 * pops back via NavigationHandler.
 */
internal interface PlanEditorStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val mode: Mode,
        val isLoading: Boolean,
        val exerciseName: String,
        val isWeighted: Boolean,
        val initialDraft: ImmutableList<PlanSetUiModel>,
        val draft: ImmutableList<PlanSetUiModel>,
        val confirmDiscardOpen: Boolean,
        val isSaving: Boolean,
    ) : Store.State {

        /** True when [draft] differs from [initialDraft]. Drives BackHandler interception. */
        val isDirty: Boolean get() = draft != initialDraft

        /**
         * BackHandler intercepts only when there are unsaved edits. Clean state pops
         * natively with predictive-back preview intact.
         */
        val interceptBack: Boolean get() = isDirty && !confirmDiscardOpen

        @Stable
        sealed interface Mode {

            /**
             * Editing the plan attached to a performed-exercise row in a live session. When
             * [trainingUuid] is null the exercise is ad-hoc (last_adhoc_sets is the backing
             * store); otherwise plan_sets on training_exercise_table is the backing store.
             */
            @Stable
            data class PerformedExercise(
                val performedExerciseUuid: String,
                val exerciseUuid: String,
                val trainingUuid: String?,
            ) : Mode

            /**
             * Editing the default plan attached to an exercise (no live session). The backing
             * store is `exercise_table.last_adhoc_sets`.
             */
            @Stable
            data class Exercise(val exerciseUuid: String) : Mode
        }

        companion object {

            fun init(mode: Mode): State = State(
                mode = mode,
                isLoading = true,
                exerciseName = "",
                isWeighted = true,
                initialDraft = persistentListOf(),
                draft = persistentListOf(),
                confirmDiscardOpen = false,
                isSaving = false,
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

            data object OnSave : Click

            data object OnBackClick : Click

            data object OnConfirmDiscard : Click

            data object OnConfirmSave : Click

            data object OnDismissDiscard : Click
        }

        sealed interface Input : Action {

            data class OnSetWeightChange(val index: Int, val value: Double?) : Input

            data class OnSetRepsChange(val index: Int, val value: Int) : Input
        }

        sealed interface Navigation : Action {

            data object Back : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event

        data class ShowError(val type: ErrorType) : Event
    }

    @Stable
    enum class ErrorType(val msgRes: Int) {
        LoadFailed(io.github.stslex.workeeper.core.ui.plan_editor.R.string.core_ui_plan_editor_error_load),
        SaveFailed(io.github.stslex.workeeper.core.ui.plan_editor.R.string.core_ui_plan_editor_error_save),
    }
}
