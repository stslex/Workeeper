// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlanEditActionHandlerTest {

    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true)
    private val initialState = State.create(uuid = null)
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<SingleTrainingHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every { launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Unit>()) } answers {
            mockk(relaxed = true)
        }
    }

    private val handler = PlanEditActionHandler(interactor, store)

    private fun openTarget(
        initial: List<PlanSetUiModel> = emptyList(),
        draft: List<PlanSetUiModel> = initial,
        exerciseUuid: String = "ex-1",
        exerciseName: String = "Bench Press",
        exerciseType: ExerciseTypeUiModel = ExerciseTypeUiModel.WEIGHTED,
    ) {
        stateFlow.value = stateFlow.value.copy(
            uuid = "training-1",
            planEditorTarget = State.PlanEditorTarget(
                exerciseUuid = exerciseUuid,
                exerciseName = exerciseName,
                exerciseType = exerciseType,
                initialPlan = persistentListOf<PlanSetUiModel>().addAll(initial),
                draft = persistentListOf<PlanSetUiModel>().addAll(draft),
            ),
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = exerciseUuid,
                    exerciseName = exerciseName,
                    exerciseType = exerciseType,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = persistentListOf<PlanSetUiModel>().addAll(initial),
                    planSummary = "",
                ),
            ),
        )
    }

    @Test
    fun `OnAddSet adds a default WORK row to an empty draft`() {
        openTarget()
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnAddSet))
        val draft = stateFlow.value.planEditorTarget?.draft
        assertEquals(1, draft?.size)
        assertEquals(SetTypeUiModel.WORK, draft?.first()?.type)
    }

    @Test
    fun `OnAddSet copies the previous row's weight and reps`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 80.0, reps = 6, type = SetTypeUiModel.FAILURE),
            ),
        )
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnAddSet))
        val draft = stateFlow.value.planEditorTarget?.draft
        assertEquals(2, draft?.size)
        val appended = draft?.last()
        assertEquals(80.0, appended?.weight)
        assertEquals(6, appended?.reps)
        // Subsequent rows always default back to WORK regardless of the prior type.
        assertEquals(SetTypeUiModel.WORK, appended?.type)
    }

    @Test
    fun `OnSetWeightChange updates the row at the target index`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditAction(AppPlanEditorAction.OnSetWeightChange(index = 1, value = 65.0)),
        )
        val draft = stateFlow.value.planEditorTarget?.draft
        assertEquals(50.0, draft?.get(0)?.weight)
        assertEquals(65.0, draft?.get(1)?.weight)
    }

    @Test
    fun `OnSetWeightChange with null clears the weight`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditAction(AppPlanEditorAction.OnSetWeightChange(index = 0, value = null)),
        )
        assertNull(stateFlow.value.planEditorTarget?.draft?.first()?.weight)
    }

    @Test
    fun `OnSetWeightChange with out-of-range index is a no-op`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        val before = stateFlow.value.planEditorTarget?.draft
        handler.invoke(
            Action.PlanEditAction(AppPlanEditorAction.OnSetWeightChange(index = 10, value = 99.0)),
        )
        assertEquals(before, stateFlow.value.planEditorTarget?.draft)
    }

    @Test
    fun `OnSetRepsChange coerces negative reps up to zero`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditAction(AppPlanEditorAction.OnSetRepsChange(index = 0, value = -3)),
        )
        assertEquals(0, stateFlow.value.planEditorTarget?.draft?.first()?.reps)
    }

    @Test
    fun `OnSetTypeChange updates the row's type`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditAction(
                AppPlanEditorAction.OnSetTypeChange(index = 0, value = SetTypeUiModel.WARMUP),
            ),
        )
        assertEquals(SetTypeUiModel.WARMUP, stateFlow.value.planEditorTarget?.draft?.first()?.type)
    }

    @Test
    fun `OnSetRemove drops the targeted row`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnSetRemove(index = 0)))
        val draft = stateFlow.value.planEditorTarget?.draft
        assertEquals(1, draft?.size)
        assertEquals(60.0, draft?.first()?.weight)
    }

    @Test
    fun `OnSetRemove with out-of-range index leaves draft unchanged`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnSetRemove(index = 5)))
        assertEquals(1, stateFlow.value.planEditorTarget?.draft?.size)
    }

    @Test
    fun `OnDismiss with clean draft closes the editor`() {
        openTarget()
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnDismiss))
        assertNull(stateFlow.value.planEditorTarget)
    }

    @Test
    fun `OnDismiss with dirty draft surfaces ShowDiscardConfirmDialog`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
            draft = listOf(
                PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnDismiss))
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it == Event.ShowDiscardConfirmDialog })
        // Editor stays open until the parent confirms or dismisses the dialog.
        assertEquals(
            "training-1",
            stateFlow.value.uuid,
        )
    }

    @Test
    fun `OnSave commits the draft to the matching exercise and closes the editor`() {
        openTarget(
            initial = persistentListOf<PlanSetUiModel>().toList(),
            draft = listOf(
                PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 90.0, reps = 3, type = SetTypeUiModel.FAILURE),
            ),
        )
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnSave))
        val updated = stateFlow.value.exercises.first { it.exerciseUuid == "ex-1" }
        assertEquals(2, updated.planSets?.size)
        assertEquals(80.0, updated.planSets?.first()?.weight)
        assertNull(stateFlow.value.planEditorTarget)
    }

    @Test
    fun `OnSave with empty draft clears the persisted plan reference on the row`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            draft = emptyList(),
        )
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnSave))
        val row = stateFlow.value.exercises.first { it.exerciseUuid == "ex-1" }
        assertNull(row.planSets)
    }

    @Test
    fun `OnSave emits a haptic ContextClick`() {
        openTarget()
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnSave))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.HapticClick)
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnAddSet on weightless exercise leaves weight null`() {
        openTarget(exerciseType = ExerciseTypeUiModel.WEIGHTLESS)
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnAddSet))
        assertNull(stateFlow.value.planEditorTarget?.draft?.first()?.weight)
    }

    @Test
    fun `actions are no-op when planEditorTarget is null`() {
        stateFlow.value = stateFlow.value.copy(planEditorTarget = null)
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnAddSet))
        handler.invoke(Action.PlanEditAction(AppPlanEditorAction.OnSetRemove(index = 0)))
        handler.invoke(
            Action.PlanEditAction(
                AppPlanEditorAction.OnSetWeightChange(index = 0, value = 1.0),
            ),
        )
        assertNull(stateFlow.value.planEditorTarget)
    }
}
