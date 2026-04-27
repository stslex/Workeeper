package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toData
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongMethod")
@ViewModelScoped
internal class PlanEditActionHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    store: SingleTrainingHandlerStore,
) : Handler<SingleTrainingStore.Action.PlanEditAction>, SingleTrainingHandlerStore by store {

    override fun invoke(action: SingleTrainingStore.Action.PlanEditAction) {
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
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        if (current.isPlanEditorDirty) {
            sendEvent(Event.ShowDiscardConfirmDialog)
        } else {
            updateState { it.copy(planEditorTarget = null) }
        }
    }

    private fun processPlanEditorSetWeight(action: AppPlanEditorAction.OnSetWeightChange) {
        updatePlanRow(action.index) { it.copy(weight = action.value) }
    }

    private fun processPlanEditorSetReps(action: AppPlanEditorAction.OnSetRepsChange) {
        updatePlanRow(action.index) { it.copy(reps = action.value.coerceAtLeast(0)) }
    }

    private fun processPlanEditorSetType(action: AppPlanEditorAction.OnSetTypeChange) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updatePlanRow(action.index) { it.copy(type = action.value) }
    }

    private fun processPlanEditorRemoveSet(action: AppPlanEditorAction.OnSetRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current

            val nextDraft = target.draft.toMutableList()
                .also { if (action.index in it.indices) it.removeAt(action.index) }
                .toImmutableList()
            current.copy(
                planEditorTarget = target.copy(draft = nextDraft),
            )
        }
    }

    private fun processPlanEditorAddSet() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val previous = target.draft.lastOrNull()
            val nextSet = previous
                ?.copy(type = SetTypeUiModel.WORK)
                ?: PlanSetUiModel(
                    weight = if (target.isWeighted) null else null,
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

    private fun processPlanEditorSave() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val target = current.planEditorTarget ?: return
        val trainingUuid = current.uuid
        val nextPlan = target.draft.takeIf { it.isNotEmpty() }
        // Update the in-memory exercise row immediately so the UI reflects the new plan
        // even before persistence completes. Plans are sub-entities of (training,
        // exercise) — independent of training-level Save.
        updateState { latest ->
            latest.copy(
                exercises = latest.exercises.map { item ->
                    if (item.exerciseUuid == target.exerciseUuid) {
                        item.copy(planSets = nextPlan)
                    } else {
                        item
                    }
                }.toImmutableList(),
                planEditorTarget = null,
            )
        }
        if (trainingUuid != null) {
            launch {
                interactor.setPlanForExercise(
                    trainingUuid = trainingUuid,
                    exerciseUuid = target.exerciseUuid,
                    plan = nextPlan?.map { it.toData() },
                )
            }
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
