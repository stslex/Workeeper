// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

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

    private val handler = ClickHandler(interactor, Dispatchers.Unconfined, store)

    @Test
    fun `OnSaveClick with blank name flips nameError`() {
        stateFlow.value = stateFlow.value.copy(name = "")
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.nameError)
    }

    @Test
    fun `OnEditClick flips into Edit mode`() {
        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read)
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is State.Mode.Edit)
    }

    @Test
    fun `OnPlanEditorDismiss with clean draft closes target`() {
        val plan = persistentListOf<PlanSetDataModel>()
        stateFlow.value = stateFlow.value.copy(
            planEditorTarget = State.PlanEditorTarget(
                exerciseUuid = "ex-1",
                exerciseName = "Bench",
                exerciseType = ExerciseTypeDataModel.WEIGHTED,
                initialPlan = plan,
                draft = plan,
            ),
        )
        handler.invoke(Action.Click.OnPlanEditorDismiss)
        assertEquals(null, stateFlow.value.planEditorTarget)
    }

    @Test
    fun `OnPlanEditorAddSet appends a default set to draft`() {
        val plan = persistentListOf<PlanSetDataModel>()
        stateFlow.value = stateFlow.value.copy(
            planEditorTarget = State.PlanEditorTarget(
                exerciseUuid = "ex-1",
                exerciseName = "Bench",
                exerciseType = ExerciseTypeDataModel.WEIGHTED,
                initialPlan = plan,
                draft = plan,
            ),
        )
        handler.invoke(Action.Click.OnPlanEditorAddSet)
        assertEquals(1, stateFlow.value.planEditorTarget?.draft?.size)
    }

    @Test
    fun `OnAddExerciseClick emits haptic`() {
        handler.invoke(Action.Click.OnAddExerciseClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.HapticClick)
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.HapticClick).type)
    }
}
