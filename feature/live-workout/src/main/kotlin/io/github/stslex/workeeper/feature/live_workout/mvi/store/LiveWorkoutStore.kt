// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
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
        val expandedDoneExerciseUuids: ImmutableSet<String>,
        val planEditorTarget: PlanEditorTarget?,
        val pendingFinishConfirm: FinishStats?,
        val pendingResetExerciseUuid: String?,
        val pendingSkipExerciseUuid: String?,
        val pendingCancelConfirm: Boolean,
        val isLoading: Boolean,
        val errorMessage: String?,
    ) : Store.State {

        @Stable
        data class DraftKey(val performedExerciseUuid: String, val position: Int)

        @Stable
        data class FinishStats(
            val durationMillis: Long,
            val durationLabel: String,
            val exercisesSummaryLabel: String,
            val setsLoggedLabel: String,
        )

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

        val elapsedMillis: Long get() = (nowMillis - startedAt).coerceAtLeast(0L)

        val isPlanEditorDirty: Boolean
            get() = planEditorTarget?.let { it.draft != it.initialPlan } == true

        val interceptBack: Boolean get() = isPlanEditorDirty

        companion object {

            fun create(sessionUuid: String?, trainingUuid: String?): State = State(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
                trainingName = "",
                trainingNameLabel = "",
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
                expandedDoneExerciseUuids = persistentSetOf(),
                planEditorTarget = null,
                pendingFinishConfirm = null,
                pendingResetExerciseUuid = null,
                pendingSkipExerciseUuid = null,
                pendingCancelConfirm = false,
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
            data class OnExerciseHeaderClick(val performedExerciseUuid: String) : Click
            data object OnBackClick : Click
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
            data object TimerTick : Common
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
