// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Suppress("ComplexInterface")
interface LiveWorkoutStore :
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
        /** The `.shead` meta line, built in the presentation mapper. Blank when no exercises. */
        val headerMetaLabel: String,
        val exercises: ImmutableList<LiveExerciseUiModel>,
        val setDrafts: ImmutableMap<DraftKey, LiveSetUiModel>,
        /**
         * Setbar's per-exercise visible-row count. Absent key = derive from
         * `max(plan, performed, drafts)`; present = exactly this many rows. Ephemeral.
         */
        val rowCountOverrides: ImmutableMap<String, Int> = persistentMapOf(),
        /** Exercises added during this session; gates the `sh-ex` one-off switch. Ephemeral. */
        val midSessionAddedUuids: ImmutableSet<String> = persistentSetOf(),
        /** Single-level undo window driving the toast; null = no toast. See [PendingUndo]. */
        val pendingUndo: PendingUndo? = null,
        /** Exercises the user explicitly started; non-empty suppresses the auto-default CURRENT. */
        val activeExerciseUuids: ImmutableSet<String>,
        /** The open cards; only a header tap writes it. Multiple open cards are legal. */
        val expandedExerciseUuids: ImmutableSet<String>,
        val preSessionPrSnapshot: ImmutableMap<String, PrSnapshotItem>,
        val isAddExerciseInFlight: Boolean,
        val isFinishInFlight: Boolean,
        val isLoading: Boolean,
        /**
         * The session could not be loaded, and the route must leave rather than render.
         * GUARD: keep it in State — an Event emitted before the screen subscribes is dropped.
         */
        val loadFailed: Boolean,
        val dialogState: DialogState,
        val bottomSheetState: BottomSheetState,
    ) : Store.State {

        @Stable
        data class DraftKey(val performedExerciseUuid: String, val position: Int)

        /** Pre-session PR snapshot for the whole session; an absent key means "no PR yet". */
        @Stable
        data class PrSnapshotItem(
            val weight: Double?,
            val reps: Int,
            val type: ExerciseTypeUiModel,
        )

        val elapsedMillis: Long get() = (nowMillis - startedAt).coerceAtLeast(0L)

        /** "Empty session" predicate driving the E1 confirm dialog. */
        val isSessionEmpty: Boolean
            get() = exercises.isEmpty() || exercises.all { it.performedSets.isEmpty() }

        /** Visible rows never filled in, over non-skipped exercises; shown in the finish dialog. */
        val unfilledSetCount: Int
            get() = exercises
                .filter { it.status != ExerciseStatusUiModel.SKIPPED }
                .sumOf { exercise -> exercise.visibleSets.count { it.isUnfilled } }

        /** Throttle gate for the mid-session add-exercise CTA. */
        val canAddExercise: Boolean
            get() = !isAddExerciseInFlight && !isFinishInFlight

        /** UI states that intercept system back; dismissal order lives in `processBackClick`. */
        val interceptBack: Boolean
            get() = isTrainingNameEditing ||
                bottomSheetState is BottomSheetState.ExercisePicker ||
                dialogState is DialogState.EmptyFinish

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
                headerMetaLabel = "",
                exercises = persistentListOf(),
                setDrafts = persistentMapOf(),
                activeExerciseUuids = persistentSetOf(),
                expandedExerciseUuids = persistentSetOf(),
                preSessionPrSnapshot = persistentMapOf(),
                isAddExerciseInFlight = false,
                isFinishInFlight = false,
                isLoading = true,
                loadFailed = false,
                dialogState = DialogState.Hidden,
                bottomSheetState = BottomSheetState.Hidden,
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

            /** The setbar's `− подход`: removes the LAST visible row. */
            data class OnRemoveLastSet(val performedExerciseUuid: String) : Click
            data class OnEditPlan(val performedExerciseUuid: String) : Click
            data class OnResetSets(val performedExerciseUuid: String) : Click
            data class OnSkipExercise(val performedExerciseUuid: String) : Click
            data object OnFinishClick : Click
            data object OnCancelSessionClick : Click
            data object OnDeleteSessionMenuClick : Click
            data class OnExerciseHeaderClick(val performedExerciseUuid: String) : Click
            data object OnBackClick : Click

            // Editable training-name header (save on blur, "Untitled" placeholder).
            data object OnTrainingNameTap : Click
            data class OnTrainingNameChange(val text: String) : Click
            data class OnTrainingNameSubmit(val text: String) : Click
            data object OnTrainingNameDismiss : Click

            // Mid-session add exercise (opens the picker sheet).
            data object OnAddExerciseClick : Click

            data object OnSessionMenuClick : Click

            data class OnExerciseMenuClick(val performedExerciseUuid: String) : Click

            /** Card `.mini.info` → `sh-desc`; only offered when a description exists. */
            data class OnShowDescription(val performedExerciseUuid: String) : Click

            /** `sh-ex`'s `Только на сегодня` switch — flips plan attachment. */
            data class OnToggleOneOff(val performedExerciseUuid: String) : Click

            data class OnDeleteExerciseClick(val performedExerciseUuid: String) : Click

            data object OnSheetDismiss : Click

            data object OnUndoClick : Click

            /** The toast's 5s window elapsed; commits [PendingUndo.deferredCommit]. */
            data class OnUndoTimeout(val id: Long) : Click
        }

        sealed interface DialogClick : Action {

            data object OnDeleteSessionConfirm : DialogClick

            /** `sh-del`'s `Удалить из плана` — commits the deletion (undoable, 5s). */
            data class OnDeleteExerciseConfirm(val performedExerciseUuid: String) : DialogClick

            data object OnDeleteExerciseKeep : DialogClick
            data object OnDeleteSessionDismiss : DialogClick
            data object OnEmptyFinishDiscard : DialogClick
            data object OnEmptyFinishContinue : DialogClick
            data object OnCancelSessionConfirm : DialogClick
            data class OnResetSetsConfirm(val performedExerciseUuid: String) : DialogClick
            data object OnResetSetsDismiss : DialogClick
            data object OnCancelSessionDismiss : DialogClick
            data object OnFinishConfirm : DialogClick
            data object OnFinishDismiss : DialogClick
            data class OnFinishNameChange(val text: String) : DialogClick

            /** Wraps picker sheet actions; delegated to `ExercisePickerHandler`. */
            @Suppress("MviActionNamingRule")
            data class PickerAction(val action: ExercisePickerAction) : DialogClick
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

            /** Navigates to the full-screen plan editor; the screen reloads on resume. */
            data class OpenPlanEditor(
                val performedExerciseUuid: String,
                val exerciseUuid: String,
                val trainingUuid: String?,
            ) : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common

            /** The plan editor returned; only `saved = true` re-runs the session load. */
            data class PlanResultReceived(val saved: Boolean) : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event
        data class HapticImpact(val type: HapticFeedbackType) : Event
        data class ShowSessionSavedSnackbar(val message: String) : Event
        data class ShowError(val message: String) : Event
    }

    @Stable
    data class ConfirmDialog(
        val title: String,
        val body: String,
        val confirmLabel: String,
        val dismissLabel: String,
    )
}
