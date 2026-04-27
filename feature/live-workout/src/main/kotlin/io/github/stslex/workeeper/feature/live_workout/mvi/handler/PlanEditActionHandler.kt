// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toData
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

private const val DEFAULT_NEW_REPS = 5

@Suppress("TooManyFunctions")
@ViewModelScoped
internal class PlanEditActionHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    store: LiveWorkoutHandlerStore,
) : Handler<LiveWorkoutStore.Action.PlanEditAction>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: LiveWorkoutStore.Action.PlanEditAction) {
        when (val inner = action.action) {
            AppPlanEditorAction.OnAddSet -> processAddSet()
            AppPlanEditorAction.OnDismiss -> processDismiss()
            AppPlanEditorAction.OnSave -> processSave()
            is AppPlanEditorAction.OnSetRemove -> processRemove(inner)
            is AppPlanEditorAction.OnSetRepsChange -> processReps(inner)
            is AppPlanEditorAction.OnSetTypeChange -> processType(inner)
            is AppPlanEditorAction.OnSetWeightChange -> processWeight(inner)
        }
    }

    private fun processDismiss() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(planEditorTarget = null) }
    }

    private fun processWeight(action: AppPlanEditorAction.OnSetWeightChange) {
        updateRow(action.index) { it.copy(weight = action.value) }
    }

    private fun processReps(action: AppPlanEditorAction.OnSetRepsChange) {
        updateRow(action.index) { it.copy(reps = action.value.coerceAtLeast(0)) }
    }

    private fun processType(action: AppPlanEditorAction.OnSetTypeChange) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateRow(action.index) { it.copy(type = action.value) }
    }

    private fun processRemove(action: AppPlanEditorAction.OnSetRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val nextDraft = target.draft.toMutableList()
                .also { if (action.index in it.indices) it.removeAt(action.index) }
                .toImmutableList()
            current.copy(planEditorTarget = target.copy(draft = nextDraft))
        }
    }

    private fun processAddSet() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val previous = target.draft.lastOrNull()
            val nextSet = previous?.copy(type = SetTypeUiModel.WORK) ?: PlanSetUiModel(
                weight = null,
                reps = DEFAULT_NEW_REPS,
                type = SetTypeUiModel.WORK,
            )
            current.copy(
                planEditorTarget = target.copy(
                    draft = (target.draft + nextSet).toImmutableList(),
                ),
            )
        }
    }

    private fun processSave() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val target = current.planEditorTarget ?: return
        val nextPlan = target.draft.takeIf { it.isNotEmpty() }
        // Optimistic plan refresh in UI; backing store update is fire-and-forget.
        updateState { latest ->
            latest.copy(
                exercises = latest.exercises.map { exercise ->
                    if (exercise.exerciseUuid == target.exerciseUuid) {
                        exercise.copy(
                            planSets = nextPlan?.toImmutableList()
                                ?: kotlinx.collections.immutable.persistentListOf(),
                        )
                    } else {
                        exercise
                    }
                }.toImmutableList(),
                planEditorTarget = null,
            )
        }
        val trainingUuid = current.trainingUuid
        val isAdhoc = current.isAdhoc
        launch(
            onError = { _ -> sendEvent(Event.ShowError(ErrorType.PlanSaveFailed)) },
        ) {
            val data = nextPlan?.map { it.toData() }
            if (isAdhoc) {
                interactor.setAdhocPlan(target.exerciseUuid, data)
            } else if (!trainingUuid.isNullOrBlank()) {
                interactor.setPlanForExercise(trainingUuid, target.exerciseUuid, data)
            }
        }
    }

    private inline fun updateRow(
        index: Int,
        crossinline transform: (PlanSetUiModel) -> PlanSetUiModel,
    ) {
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            if (index !in target.draft.indices) return@updateState current
            val next = target.draft.toMutableList()
                .apply { this[index] = transform(this[index]) }
                .toImmutableList()
            current.copy(planEditorTarget = target.copy(draft = next))
        }
    }
}
