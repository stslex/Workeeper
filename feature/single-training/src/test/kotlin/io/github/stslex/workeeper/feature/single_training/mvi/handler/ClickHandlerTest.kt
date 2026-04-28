// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        every {
            launch(
                any(),
                any(),
                any(),
                any(),
                any<suspend CoroutineScope.() -> Unit>(),
            )
        } answers {
            mockk(relaxed = true)
        }
    }

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    private val handler = ClickHandler(interactor, resourceWrapper, Dispatchers.Unconfined, store)

    @Test
    fun `OnSaveClick with blank name flips nameError`() {
        stateFlow.value = stateFlow.value.copy(name = "")
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.nameError)
    }

    @Test
    fun `OnSaveClick with empty exercises emits ShowSaveError`() {
        stateFlow.value = stateFlow.value.copy(name = "Push Day")
        handler.invoke(Action.Click.OnSaveClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.ShowSaveError)
    }

    @Test
    fun `OnEditClick flips into Edit mode`() {
        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read)
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is State.Mode.Edit)
    }

    @Test
    fun `OnEditPlanClick stages planEditorTarget from selected row`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
        )
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Bench Press",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = plan,
                    planSummary = "80×5",
                ),
            ),
        )
        handler.invoke(Action.Click.OnEditPlanClick("ex-1"))
        val target = stateFlow.value.planEditorTarget
        assertNotNull(target)
        assertEquals("ex-1", target?.exerciseUuid)
        assertEquals("Bench Press", target?.exerciseName)
        assertEquals(ExerciseTypeUiModel.WEIGHTED, target?.exerciseType)
        assertEquals(plan, target?.initialPlan)
        assertEquals(plan, target?.draft)
    }

    @Test
    fun `OnEditPlanClick is no-op when exercise uuid is unknown`() {
        stateFlow.value = stateFlow.value.copy(exercises = persistentListOf())
        handler.invoke(Action.Click.OnEditPlanClick("missing"))
        assertEquals(null, stateFlow.value.planEditorTarget)
    }

    @Test
    fun `OnBackClick with dirty plan editor draft surfaces ShowDiscardConfirmDialog`() {
        val initial = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        val dirty = persistentListOf(
            PlanSetUiModel(weight = 70.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        stateFlow.value = stateFlow.value.copy(
            planEditorTarget = State.PlanEditorTarget(
                exerciseUuid = "ex-1",
                exerciseName = "Bench",
                exerciseType = ExerciseTypeUiModel.WEIGHTED,
                initialPlan = initial,
                draft = dirty,
            ),
        )
        handler.invoke(Action.Click.OnBackClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it == Event.ShowDiscardConfirmDialog })
    }

    @Test
    fun `OnConfirmDiscard with dirty plan editor closes editor without popping`() {
        val initial = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        val dirty = persistentListOf(
            PlanSetUiModel(weight = 70.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        stateFlow.value = stateFlow.value.copy(
            planEditorTarget = State.PlanEditorTarget(
                exerciseUuid = "ex-1",
                exerciseName = "Bench",
                exerciseType = ExerciseTypeUiModel.WEIGHTED,
                initialPlan = initial,
                draft = dirty,
            ),
        )
        handler.invoke(Action.Click.OnConfirmDiscard)
        assertEquals(null, stateFlow.value.planEditorTarget)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnExerciseRemove removes the exercise and reindexes positions`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = null,
                    planSummary = "",
                ),
                TrainingExerciseItem(
                    exerciseUuid = "ex-2",
                    exerciseName = "Squat",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 1,
                    planSets = null,
                    planSummary = "",
                ),
            ),
        )
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val remaining = stateFlow.value.exercises
        assertEquals(1, remaining.size)
        assertEquals("ex-2", remaining[0].exerciseUuid)
        assertEquals(0, remaining[0].position)
    }

    @Test
    fun `OnExerciseReorder swaps positions on a non-trivial move`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = null,
                    planSummary = "",
                ),
                TrainingExerciseItem(
                    exerciseUuid = "ex-2",
                    exerciseName = "Squat",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 1,
                    planSets = null,
                    planSummary = "",
                ),
            ),
        )
        handler.invoke(Action.Click.OnExerciseReorder(from = 0, to = 1))
        val ordered = stateFlow.value.exercises
        assertEquals("ex-2", ordered[0].exerciseUuid)
        assertEquals(0, ordered[0].position)
        assertEquals("ex-1", ordered[1].exerciseUuid)
        assertEquals(1, ordered[1].position)
    }

    @Test
    fun `OnExerciseReorder with from equal to to is a no-op`() {
        val items = persistentListOf(
            TrainingExerciseItem(
                exerciseUuid = "ex-1",
                exerciseName = "Bench",
                exerciseType = ExerciseTypeUiModel.WEIGHTED,
                tags = persistentListOf(),
                position = 0,
                planSets = null,
                planSummary = "",
            ),
        )
        stateFlow.value = stateFlow.value.copy(exercises = items)
        handler.invoke(Action.Click.OnExerciseReorder(from = 0, to = 0))
        assertEquals(items, stateFlow.value.exercises)
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
