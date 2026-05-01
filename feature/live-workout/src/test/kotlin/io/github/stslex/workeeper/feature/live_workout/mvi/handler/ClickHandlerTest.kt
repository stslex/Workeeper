// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val pickerHandler = mockk<ExercisePickerHandler>(relaxed = true)

    @Test
    fun `OnExerciseHeaderClick toggles expansion for DONE exercises`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.DONE)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(
            persistentSetOf("pe-1"),
            stateFlow.value.expandedExerciseUuids,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(
            persistentSetOf<String>(),
            stateFlow.value.expandedExerciseUuids,
        )
    }

    @Test
    fun `OnExerciseHeaderClick is no-op for SKIPPED exercises`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.SKIPPED)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))

        assertEquals(persistentSetOf<String>(), stateFlow.value.expandedExerciseUuids)
        assertEquals(persistentSetOf<String>(), stateFlow.value.activeExerciseUuids)
    }

    @Test
    fun `OnExerciseHeaderClick on PENDING adds uuid to activeExerciseUuids and expandedExerciseUuids`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.PENDING))
                .copy(
                    exercises = persistentListOf(
                        doneExercise(status = ExerciseStatusUiModel.CURRENT),
                        doneExercise(status = ExerciseStatusUiModel.PENDING).copy(
                            performedExerciseUuid = "pe-2",
                            exerciseUuid = "ex-2",
                            position = 1,
                        ),
                    ),
                ),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-2"))

        assertEquals(persistentSetOf("pe-2"), stateFlow.value.activeExerciseUuids)
        assertEquals(persistentSetOf("pe-2"), stateFlow.value.expandedExerciseUuids)
        // Status of pe-2 flips to CURRENT after recompute.
        val pe2 = stateFlow.value.exercises.first { it.performedExerciseUuid == "pe-2" }
        assertEquals(ExerciseStatusUiModel.CURRENT, pe2.status)
    }

    @Test
    fun `OnExerciseHeaderClick on auto-default CURRENT promotes to active and toggles expanded`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))

        assertEquals(persistentSetOf("pe-1"), stateFlow.value.activeExerciseUuids)
        assertEquals(persistentSetOf("pe-1"), stateFlow.value.expandedExerciseUuids)

        // Tapping again collapses (removes from expanded), but the uuid stays in activeUuids.
        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(persistentSetOf("pe-1"), stateFlow.value.activeExerciseUuids)
        assertEquals(persistentSetOf<String>(), stateFlow.value.expandedExerciseUuids)
    }

    @Test
    fun `OnDeleteSessionMenuClick flips deleteDialogVisible true`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionMenuClick)

        assertEquals(true, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun `OnDeleteSessionDismiss flips deleteDialogVisible false`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT))
                .copy(deleteDialogVisible = true),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionDismiss)

        assertEquals(false, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun `OnDeleteSessionConfirm clears dialog without sessionUuid`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT))
                .copy(sessionUuid = null, deleteDialogVisible = true),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionConfirm)

        assertEquals(false, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun `OnDeleteSessionConfirm with sessionUuid hides dialog`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT))
                .copy(deleteDialogVisible = true),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionConfirm)

        assertEquals(false, stateFlow.value.deleteDialogVisible)
    }

    private fun handlerStore(stateFlow: MutableStateFlow<State>): LiveWorkoutHandlerStore =
        mockk(relaxed = true) {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }

    private fun baseState(exercise: LiveExerciseUiModel): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        isLoading = false,
        exercises = persistentListOf(exercise),
    )

    private fun doneExercise(status: ExerciseStatusUiModel): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = "pe-1",
        exerciseUuid = "ex-1",
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = status,
        statusLabel = "",
        planSets = persistentListOf(),
        performedSets = persistentListOf(),
    )
}
