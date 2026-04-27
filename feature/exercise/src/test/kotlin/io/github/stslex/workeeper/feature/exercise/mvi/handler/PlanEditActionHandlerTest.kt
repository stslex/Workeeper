// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlanEditActionHandlerTest {

    private val initialState = State.create(uuid = "uuid-1")
    private val stateFlow = MutableStateFlow(initialState)
    private val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
    }

    private val handler = PlanEditActionHandler(store)

    private fun openTarget(
        initial: List<PlanSetUiModel> = emptyList(),
        draft: List<PlanSetUiModel> = initial,
    ) {
        stateFlow.value = stateFlow.value.copy(
            planEditorTarget = State.PlanEditorTarget(
                initialPlan = persistentListOf<PlanSetUiModel>().addAll(initial),
                draft = persistentListOf<PlanSetUiModel>().addAll(draft),
            ),
        )
    }

    @Test
    fun `OnAddSet adds a default WORK row to an empty draft`() {
        openTarget()
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnAddSet))
        val draftRow = stateFlow.value.planEditorTarget?.draft?.first()
        assertNotNull(draftRow)
        assertEquals(SetTypeUiModel.WORK, draftRow?.type)
        assertNull(draftRow?.weight)
    }

    @Test
    fun `OnAddSet copies previous row's weight and reps but resets type to WORK`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 70.0, reps = 5, type = SetTypeUiModel.WARMUP),
            ),
        )
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnAddSet))
        val draft = stateFlow.value.planEditorTarget?.draft
        assertEquals(2, draft?.size)
        val appended = draft?.last()
        assertEquals(70.0, appended?.weight)
        assertEquals(5, appended?.reps)
        assertEquals(SetTypeUiModel.WORK, appended?.type)
    }

    @Test
    fun `OnSetWeightChange updates the targeted row`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditorAction(AppPlanEditorAction.OnSetWeightChange(index = 1, value = 65.5)),
        )
        assertEquals(50.0, stateFlow.value.planEditorTarget?.draft?.get(0)?.weight)
        assertEquals(65.5, stateFlow.value.planEditorTarget?.draft?.get(1)?.weight)
    }

    @Test
    fun `OnSetRepsChange clamps negative values to zero`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditorAction(AppPlanEditorAction.OnSetRepsChange(index = 0, value = -10)),
        )
        assertEquals(0, stateFlow.value.planEditorTarget?.draft?.first()?.reps)
    }

    @Test
    fun `OnSetTypeChange swaps the type and emits a haptic`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(
            Action.PlanEditorAction(
                AppPlanEditorAction.OnSetTypeChange(index = 0, value = SetTypeUiModel.DROP),
            ),
        )
        assertEquals(SetTypeUiModel.DROP, stateFlow.value.planEditorTarget?.draft?.first()?.type)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.Haptic)
    }

    @Test
    fun `OnSetRemove drops the targeted row`() {
        openTarget(
            initial = listOf(
                PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            ),
        )
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnSetRemove(index = 1)))
        assertEquals(1, stateFlow.value.planEditorTarget?.draft?.size)
        assertEquals(50.0, stateFlow.value.planEditorTarget?.draft?.first()?.weight)
    }

    @Test
    fun `OnDismiss commits the draft into adhocPlan and closes the editor`() {
        val draft = listOf(
            PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        openTarget(initial = emptyList(), draft = draft)
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnDismiss))
        assertNull(stateFlow.value.planEditorTarget)
        assertEquals(persistentListOf<PlanSetUiModel>().addAll(draft), stateFlow.value.adhocPlan)
    }

    @Test
    fun `OnSave commits the draft into adhocPlan and closes the editor`() {
        val draft = listOf(
            PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 90.0, reps = 3, type = SetTypeUiModel.FAILURE),
        )
        openTarget(initial = emptyList(), draft = draft)
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnSave))
        assertNull(stateFlow.value.planEditorTarget)
        assertEquals(2, stateFlow.value.adhocPlan?.size)
    }

    @Test
    fun `OnSave emits a haptic ContextClick`() {
        openTarget()
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnSave))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.Haptic)
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.Haptic).type)
    }

    @Test
    fun `actions are no-op when planEditorTarget is null`() {
        stateFlow.value = stateFlow.value.copy(planEditorTarget = null)
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnAddSet))
        handler.invoke(
            Action.PlanEditorAction(
                AppPlanEditorAction.OnSetWeightChange(index = 0, value = 1.0),
            ),
        )
        handler.invoke(Action.PlanEditorAction(AppPlanEditorAction.OnSetRemove(index = 0)))
        assertNull(stateFlow.value.planEditorTarget)
    }
}
