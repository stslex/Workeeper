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
        /**
         * The `.shead` meta line (extraction §1.3), built in the presentation mapper:
         * `{fin} из {act} упражнений · {d} из {t} подходов`, plus ` · пропущено {sk}` only
         * when anything is skipped. `fin` counts exercises where every set is done, `act`
         * excludes skipped, `d`/`t` count sets over non-skipped exercises only. Blank while
         * the session has no exercises.
         */
        val headerMetaLabel: String,
        val exercises: ImmutableList<LiveExerciseUiModel>,
        val setDrafts: ImmutableMap<DraftKey, LiveSetUiModel>,
        /**
         * Per-exercise visible-row count set by the setbar (`+ подход` / `− подход`,
         * extraction §1.7). Absent key = derive from `max(plan, performed, drafts)` as
         * always; present = exactly this many rows (floored at the highest performed
         * position, which deletion clears first). Lets `− подход` truncate BELOW the plan
         * length — the plan itself is untouched, exactly like the drafts layer this sits
         * beside. Ephemeral (§6.1's draft-state rule): a reload re-derives rows from the
         * plan.
         */
        val rowCountOverrides: ImmutableMap<String, Int> = persistentMapOf(),
        /**
         * Exercises added DURING this session (picker adds, both library and inline). Gates
         * the `sh-ex` one-off switch (§6.1: "the toggle appears only on mid-session
         * additions") and picks `sh-del`'s adhoc body. Ephemeral by design — a process
         * restore loses it, and a loaded one-off keeps its toggle via `!isPlanAttached`.
         */
        val midSessionAddedUuids: ImmutableSet<String> = persistentSetOf(),
        /**
         * The single-level undo window driving the toast (extraction §1.9). Null = no toast.
         * See [PendingUndo] for the replace/commit semantics.
         */
        val pendingUndo: PendingUndo? = null,
        /**
         * UUIDs the user has explicitly tapped to start (or kept active across recompute).
         * When non-empty, the auto-default first-CURRENT behavior is suppressed; only
         * exercises in this set become CURRENT (alongside SKIPPED/DONE derivation).
         * Ephemeral — resets on app background/restore.
         */
        val activeExerciseUuids: ImmutableSet<String>,
        /**
         * The open cards — the whole disclosure model, by decision (the session-rebuild
         * amendment, superseding spec §7's seven-rule automaton): expanded means open,
         * nothing more. First entry opens the first card; a header tap flips exactly this
         * set's membership for that card; NOTHING else ever writes it (no auto-advance, no
         * auto-collapse-on-completion, no "exactly one open"). Multiple open cards are legal
         * and expected. Lives in the Store so a plan-editor round-trip preserves it.
         */
        val expandedExerciseUuids: ImmutableSet<String>,
        val preSessionPrSnapshot: ImmutableMap<String, PrSnapshotItem>,
        val isAddExerciseInFlight: Boolean,
        val isFinishInFlight: Boolean,
        val isLoading: Boolean,
        val dialogState: DialogState,
        val bottomSheetState: BottomSheetState,
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

        val elapsedMillis: Long get() = (nowMillis - startedAt).coerceAtLeast(0L)

        /**
         * "Empty session" predicate driving the E1 confirm dialog: no exercises at all,
         * or every exercise has zero performed sets.
         */
        val isSessionEmpty: Boolean
            get() = exercises.isEmpty() || exercises.all { it.performedSets.isEmpty() }

        /**
         * Visible rows the user never filled in, across every non-skipped exercise. Surfaced
         * in `FinishConfirmDialog` so the discard at finish is stated rather than silent
         * (§6.1). Skipped exercises are excluded — their rows are already outside the
         * progress denominator, so counting them would overstate the loss.
         */
        val unfilledSetCount: Int
            get() = exercises
                .filter { it.status != ExerciseStatusUiModel.SKIPPED }
                .sumOf { exercise -> exercise.visibleSets.count { it.isUnfilled } }

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
         * name edit → default back. Plan-editor is now its own full-screen route (v2.4
         * D1) so it owns its own BackHandler and is not part of LiveWorkout's intercept
         * conditions.
         */
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

            /**
             * The setbar's `− подход` (§6.4): removes the LAST visible row — middle deletion
             * is not planned. Disabled in UI at one row; the handler guards it again.
             */
            data class OnRemoveLastSet(val performedExerciseUuid: String) : Click
            data class OnEditPlan(val performedExerciseUuid: String) : Click
            data class OnResetSets(val performedExerciseUuid: String) : Click
            data class OnSkipExercise(val performedExerciseUuid: String) : Click
            data object OnFinishClick : Click
            data object OnCancelSessionClick : Click
            data object OnDeleteSessionMenuClick : Click
            data class OnExerciseHeaderClick(val performedExerciseUuid: String) : Click
            data object OnBackClick : Click

            // v2.3 — editable training-name header (save on blur, "Untitled" placeholder).
            data object OnTrainingNameTap : Click
            data class OnTrainingNameChange(val text: String) : Click
            data class OnTrainingNameSubmit(val text: String) : Click
            data object OnTrainingNameDismiss : Click

            // v2.3 — mid-session add exercise (opens the picker sheet).
            data object OnAddExerciseClick : Click

            // v3 sheets (extraction §1.9).
            /** Topbar `⋮` → `sh-session`. */
            data object OnSessionMenuClick : Click

            /** Card `.mini.menu` → `sh-ex`. */
            data class OnExerciseMenuClick(val performedExerciseUuid: String) : Click

            /** Card `.mini.info` → `sh-desc`; only offered when a description exists. */
            data class OnShowDescription(val performedExerciseUuid: String) : Click

            /** `sh-ex`'s `Только на сегодня` switch — flips plan attachment (§6.2). */
            data class OnToggleOneOff(val performedExerciseUuid: String) : Click

            /** `sh-ex`'s delete item → `sh-del`. */
            data class OnDeleteExerciseClick(val performedExerciseUuid: String) : Click

            /** Scrim tap / system dismiss for the v3 sheets. */
            data object OnSheetDismiss : Click

            /** The toast's `Отменить`. */
            data object OnUndoClick : Click

            /** The toast's 5s window elapsed; commits [PendingUndo.deferredCommit]. */
            data class OnUndoTimeout(val id: Long) : Click
        }

        sealed interface DialogClick : Action {

            data object OnDeleteSessionConfirm : DialogClick

            /** `sh-del`'s `Удалить из плана` — commits the §6.1 deletion (undoable, 5s). */
            data class OnDeleteExerciseConfirm(val performedExerciseUuid: String) : DialogClick

            /** `sh-del`'s `Оставить` — closes the sheet, nothing changes. */
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

            /**
             * Wraps the picker bottom-sheet action surface so the feature's top-level
             * DialogClick variants stay flat. `ClickHandler` delegates to the dedicated
             * `ExercisePickerHandler` when this variant fires (per the action-wrapper
             * pattern from architecture docs).
             */
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

            /**
             * Navigates to the full-screen plan editor (v2.4 D1). Replaces the legacy
             * bottom-sheet editor that lived on this screen. The screen reloads its
             * session on resume to pick up persisted edits.
             */
            data class OpenPlanEditor(
                val performedExerciseUuid: String,
                val exerciseUuid: String,
                val trainingUuid: String?,
            ) : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common

            /**
             * Triggered by the LiveWorkoutGraph after returning from the PlanEditor
             * route with a saved-flag set. Re-runs the session-load pipeline so the
             * new plan is reflected on the next composition.
             */
            data object Reload : Common
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
