package io.github.stslex.workeeper.feature.exercise.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongMethod")
@ViewModelScoped
internal class PlanEditActionHandler @Inject constructor(
    store: ExerciseHandlerStore,
) : Handler<Action.PlanEditorAction>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.PlanEditorAction) {
        when (val action = action.action) {
            AppPlanEditorAction.OnAddSet -> processPlanEditorAddSet()
            AppPlanEditorAction.OnDismiss -> processPlanEditorDismiss()
            AppPlanEditorAction.OnSave -> processPlanEditorSave()
            is AppPlanEditorAction.OnSetRemove -> processPlanEditorRemoveSet(action)
            is AppPlanEditorAction.OnSetRepsChange -> processPlanEditorSetReps(action)
            is AppPlanEditorAction.OnSetTypeChange -> processPlanEditorSetType(action)
            is AppPlanEditorAction.OnSetWeightChange -> processPlanEditorSetWeight(action)
        }
    }

    private fun processPlanEditorDismiss() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                adhocPlan = it.planEditorTarget?.draft,
                planEditorTarget = null,
            )
        }
        // save when exit from feature
    }

    private fun processPlanEditorSave() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                adhocPlan = it.planEditorTarget?.draft,
                planEditorTarget = null,
            )
        }
        // save when exit from feature
    }

    private fun processPlanEditorSetWeight(action: AppPlanEditorAction.OnSetWeightChange) {
        updatePlanRow(action.index) { it.copy(weight = action.value) }
    }

    private fun processPlanEditorSetReps(action: AppPlanEditorAction.OnSetRepsChange) {
        updatePlanRow(action.index) { it.copy(reps = action.value.coerceAtLeast(0)) }
    }

    private fun processPlanEditorSetType(action: AppPlanEditorAction.OnSetTypeChange) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updatePlanRow(action.index) { it.copy(type = action.value) }
    }

    private fun processPlanEditorRemoveSet(action: AppPlanEditorAction.OnSetRemove) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val nextDraft = target.draft.toMutableList()
                .also { if (action.index in it.indices) it.removeAt(action.index) }
                .toImmutableList()
            current.copy(planEditorTarget = target.copy(draft = nextDraft))
        }
    }

    private fun processPlanEditorAddSet() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val previous = target.draft.lastOrNull()
            val nextSet = previous?.copy(
                type = SetTypeUiModel.WORK,
            ) ?: PlanSetUiModel(
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

    private inline fun updatePlanRow(
        index: Int,
        crossinline transform: (PlanSetUiModel) -> PlanSetUiModel,
    ) {
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            if (index !in target.draft.indices) return@updateState current
            val next = target.draft.toMutableList().apply { this[index] = transform(this[index]) }
                .toImmutableList()
            current.copy(planEditorTarget = target.copy(draft = next))
        }
    }

    companion object {

        private const val DEFAULT_NEW_REPS = 5
    }
}
