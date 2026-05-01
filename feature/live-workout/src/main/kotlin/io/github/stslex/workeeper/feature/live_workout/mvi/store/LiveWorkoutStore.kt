// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Suppress("ComplexInterface")
internal interface LiveWorkoutStore :
    Store<LiveWorkoutStore.State, LiveWorkoutStore.Action, LiveWorkoutStore.Event> {

    @Stable
    data class State(
        val sessionUuid: String?,
        val trainingUuid: String?,
        val trainingName: String,
        val trainingNameLabel: String,
        val trainingNameDraft: String,
        val isTrainingNameEditing: Boolean,
        val isAdhoc: Boolean,
        val startedAt: Long,
        val nowMillis: Long,
        val elapsedDurationLabel: String,
        val doneCount: Int,
        val totalCount: Int,
        val setsLogged: Int,
        val progress: Float,
        val progressLabel: String,
        val exercises: ImmutableList<LiveExerciseUiModel>,
        val setDrafts: ImmutableMap<DraftKey, LiveSetUiModel>,
        /**
         * UUIDs the user has explicitly tapped to start (or kept active across recompute).
         * When non-empty, the auto-default first-CURRENT behavior is suppressed; only
         * exercises in this set become CURRENT (alongside SKIPPED/DONE derivation).
         * Ephemeral — resets on app background/restore.
         */
        val activeExerciseUuids: ImmutableSet<String>,
        /**
         * UUIDs the user has explicitly toggled expanded. Covers both DONE rows the user
         * pulled open and CURRENT rows the user collapsed/re-expanded. Pruned by
         * `recomputeStatuses` when an exercise transitions out of DONE/CURRENT.
         */
        val expandedExerciseUuids: ImmutableSet<String>,
        val preSessionPrSnapshot: ImmutableMap<String, PrSnapshotItem>,
        val planEditorTarget: PlanEditorTarget?,
        val pendingFinishConfirm: FinishStats?,
        val pendingResetExerciseUuid: String?,
        val pendingSkipExerciseUuid: String?,
        val pendingCancelConfirm: Boolean,
        val deleteDialogVisible: Boolean,
        val exercisePickerSheet: ExercisePickerSheetState,
        val emptyFinishDialog: EmptyFinishDialogState,
        val isAddExerciseInFlight: Boolean,
        val isFinishInFlight: Boolean,
        val isLoading: Boolean,
        val errorMessage: String?,
    ) : Store.State {

        @Stable
        data class DraftKey(val performedExerciseUuid: String, val position: Int)

        /**
         * Pre-session PR snapshot held in State for the entire session (Q6 lock — frozen
         * snapshot scope). One entry per exercise; absent key means "no PR yet" and any
         * non-zero candidate beats it. Identity (`setUuid`) is intentionally absent — the
         * comparator paths only need weight + reps + type.
         */
        @Stable
        data class PrSnapshotItem(
            val weight: Double?,
            val reps: Int,
            val type: ExerciseTypeUiModel,
        )

        @Stable
        data class FinishStats(
            val durationMillis: Long,
            val durationLabel: String,
            val exercisesSummaryLabel: String,
            val setsLoggedLabel: String,
            val newPersonalRecords: ImmutableList<NewPrEntry>,
        ) {

            @Stable
            data class NewPrEntry(
                val exerciseUuid: String,
                val exerciseName: String,
                val displayLabel: String,
            )
        }

        @Stable
        data class PlanEditorTarget(
            val performedExerciseUuid: String,
            val exerciseUuid: String,
            val exerciseName: String,
            val exerciseType: ExerciseTypeUiModel,
            val initialPlan: ImmutableList<PlanSetUiModel>,
            val draft: ImmutableList<PlanSetUiModel>,
        ) {
            val isWeighted: Boolean get() = exerciseType == ExerciseTypeUiModel.WEIGHTED
        }

        /**
         * Inline exercise picker bottom-sheet state. Display strings (no-match headline,
         * Create CTA label) are pre-formatted in the handler so the kit composable does
         * not derive text — keeps the picker locale-shape agnostic.
         */
        @Stable
        sealed interface ExercisePickerSheetState {
            data object Hidden : ExercisePickerSheetState

            @Stable
            data class Visible(
                val query: String,
                val results: ImmutableList<ExercisePickerUiModel>,
                val noMatchHeadline: String?,
                val createCtaLabel: String?,
            ) : ExercisePickerSheetState
        }

        /**
         * Empty-finish confirm dialog (E1 lock). Triggered when the user taps Finish on a
         * session with no performed sets. Discard CTA is enabled only for ad-hoc trainings;
         * library training sessions get a Continue-editing-only variant — we do not delete
         * library trainings via session cancellation.
         */
        @Stable
        sealed interface EmptyFinishDialogState {
            data object Hidden : EmptyFinishDialogState

            @Stable
            data class Visible(
                val canDiscard: Boolean,
            ) : EmptyFinishDialogState
        }

        val elapsedMillis: Long get() = (nowMillis - startedAt).coerceAtLeast(0L)

        val isPlanEditorDirty: Boolean
            get() = planEditorTarget?.let { it.draft != it.initialPlan } == true

        val isPickerVisible: Boolean
            get() = exercisePickerSheet is ExercisePickerSheetState.Visible

        val isEmptyFinishDialogVisible: Boolean
            get() = emptyFinishDialog is EmptyFinishDialogState.Visible

        /**
         * "Empty session" predicate driving the E1 confirm dialog: no exercises at all,
         * or every exercise has zero performed sets.
         */
        val isSessionEmpty: Boolean
            get() = exercises.isEmpty() || exercises.all { it.performedSets.isEmpty() }

        /**
         * Throttle gate for the mid-session add-exercise CTA. False during an in-flight
         * fetch (picker primary action disabled) and during the finish flow so the user
         * cannot stack a parallel add on top of session teardown.
         */
        val canAddExercise: Boolean
            get() = !isAddExerciseInFlight && !isFinishInFlight

        /**
         * Tracks every UI state that needs to intercept the system back gesture so the
         * Android 13+ predictive back preview stays alive everywhere else. Dismissal order
         * is enforced by `ClickHandler.processBackClick`: picker → empty-finish dialog →
         * name edit → plan-editor dirty → default back.
         */
        val interceptBack: Boolean
            get() = isPlanEditorDirty ||
                isTrainingNameEditing ||
                isPickerVisible ||
                isEmptyFinishDialogVisible

        companion object {

            fun create(sessionUuid: String?, trainingUuid: String?): State = State(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
                trainingName = "",
                trainingNameLabel = "",
                trainingNameDraft = "",
                isTrainingNameEditing = false,
                isAdhoc = false,
                startedAt = 0L,
                nowMillis = 0L,
                elapsedDurationLabel = "00:00",
                doneCount = 0,
                totalCount = 0,
                setsLogged = 0,
                progress = 0f,
                progressLabel = "",
                exercises = persistentListOf(),
                setDrafts = persistentMapOf(),
                activeExerciseUuids = persistentSetOf(),
                expandedExerciseUuids = persistentSetOf(),
                preSessionPrSnapshot = persistentMapOf(),
                planEditorTarget = null,
                pendingFinishConfirm = null,
                pendingResetExerciseUuid = null,
                pendingSkipExerciseUuid = null,
                pendingCancelConfirm = false,
                deleteDialogVisible = false,
                exercisePickerSheet = ExercisePickerSheetState.Hidden,
                emptyFinishDialog = EmptyFinishDialogState.Hidden,
                isAddExerciseInFlight = false,
                isFinishInFlight = false,
                isLoading = true,
                errorMessage = null,
            )
        }
    }

    @Suppress("ComplexInterface")
    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {
            data class OnSetMarkDone(val performedExerciseUuid: String, val position: Int) : Click
            data class OnSetUncheck(val performedExerciseUuid: String, val position: Int) : Click
            data class OnSetTypeSelect(
                val performedExerciseUuid: String,
                val position: Int,
                val type: SetTypeUiModel,
            ) : Click

            data class OnSetRemove(val performedExerciseUuid: String, val position: Int) : Click
            data class OnAddSet(val performedExerciseUuid: String) : Click
            data class OnEditPlan(val performedExerciseUuid: String) : Click
            data class OnResetSets(val performedExerciseUuid: String) : Click
            data class OnResetSetsConfirm(val performedExerciseUuid: String) : Click
            data object OnResetSetsDismiss : Click
            data class OnSkipExercise(val performedExerciseUuid: String) : Click
            data class OnSkipExerciseConfirm(val performedExerciseUuid: String) : Click
            data object OnSkipExerciseDismiss : Click
            data object OnFinishClick : Click
            data object OnFinishConfirm : Click
            data object OnFinishDismiss : Click
            data object OnCancelSessionClick : Click
            data object OnCancelSessionConfirm : Click
            data object OnCancelSessionDismiss : Click
            data object OnDeleteSessionMenuClick : Click
            data object OnDeleteSessionConfirm : Click
            data object OnDeleteSessionDismiss : Click
            data class OnExerciseHeaderClick(val performedExerciseUuid: String) : Click
            data object OnBackClick : Click

            // v2.3 — editable training-name header (save on blur, "Untitled" placeholder).
            data object OnTrainingNameTap : Click
            data class OnTrainingNameChange(val text: String) : Click
            data class OnTrainingNameSubmit(val text: String) : Click
            data object OnTrainingNameDismiss : Click

            // v2.3 — mid-session add exercise (opens the picker sheet).
            data object OnAddExerciseClick : Click

            /**
             * Wraps the picker bottom-sheet action surface so the feature's top-level
             * Click variants stay flat. `ClickHandler` delegates to the dedicated
             * `ExercisePickerHandler` when this variant fires (per the action-wrapper
             * pattern from architecture docs).
             */
            @Suppress("MviActionNamingRule")
            data class PickerAction(val action: ExercisePickerAction) : Click

            // v2.3 — empty-finish confirm dialog (E1: Discard or Continue editing).
            data object OnEmptyFinishDiscard : Click
            data object OnEmptyFinishContinue : Click
        }

        sealed interface Input : Action {
            data class OnSetWeightChange(
                val performedExerciseUuid: String,
                val position: Int,
                val value: Double?,
            ) : Input

            data class OnSetRepsChange(
                val performedExerciseUuid: String,
                val position: Int,
                val value: Int?,
            ) : Input
        }

        sealed interface Navigation : Action {
            data object Back : Navigation
            data class OpenPastSession(val sessionUuid: String) : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common
        }

        @Suppress("MviActionNamingRule")
        data class PlanEditAction(val action: AppPlanEditorAction) : Action
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event
        data class HapticImpact(val type: HapticFeedbackType) : Event
        data class ShowSessionSavedSnackbar(val message: String) : Event
        data class ShowError(val message: String) : Event
        data object ShowFinishConfirmDialog : Event
        data class ShowResetSetsConfirmDialog(val dialog: ConfirmDialog) : Event
        data class ShowSkipExerciseConfirmDialog(val dialog: ConfirmDialog) : Event
        data class ShowCancelSessionConfirmDialog(val dialog: ConfirmDialog) : Event
    }

    @Stable
    data class ConfirmDialog(
        val title: String,
        val body: String,
        val confirmLabel: String,
        val dismissLabel: String,
    )
}
