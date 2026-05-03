// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.settings.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ArchiveClickHandlerTest {

    private val interactor = mockk<SettingsInteractor>(relaxed = true)
    private val emptyPaging: PagingUiState<androidx.paging.PagingData<ArchivedItemUi.Exercise>> =
        PagingUiState {
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<ArchivedItemUi.Exercise>())
        }
    private val emptyTrainingPaging: PagingUiState<androidx.paging.PagingData<ArchivedItemUi.Training>> =
        PagingUiState {
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<ArchivedItemUi.Training>())
        }

    private val initialState = State(
        selectedSegment = Segment.EXERCISES,
        exerciseCount = 0,
        trainingCount = 0,
        archivedExercisesPaging = emptyPaging,
        archivedTrainingsPaging = emptyTrainingPaging,
        pendingDeleteImpact = null,
        pendingDeleteTarget = null,
        deleteImpactLoading = false,
    )

    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<ArchiveHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
    }

    private val handler = ArchiveClickHandler(interactor, store)

    @Test
    fun `OnSegmentChange updates selectedSegment and emits SegmentTick haptic`() {
        handler.invoke(Action.Click.OnSegmentChange(Segment.TRAININGS))
        assertEquals(Segment.TRAININGS, stateFlow.value.selectedSegment)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.SegmentTick)
    }

    @Test
    fun `OnSegmentChange to current segment is no-op`() {
        handler.invoke(Action.Click.OnSegmentChange(Segment.EXERCISES))
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnRestoreClick emits ContextClick haptic`() {
        coEvery { interactor.restoreExercise(any()) } returns Unit
        handler.invoke(Action.Click.OnRestoreClick(exerciseItem()))
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
    }

    @Test
    fun `OnUndoRestore emits ContextClick haptic`() {
        coEvery { interactor.reArchiveExercise(any()) } returns Unit
        handler.invoke(Action.Click.OnUndoRestore(exerciseItem()))
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.ContextClick)
    }

    @Test
    fun `OnDeleteDismiss does not emit haptic`() {
        stateFlow.value = stateFlow.value.copy(
            pendingDeleteTarget = exerciseItem(),
            pendingDeleteImpact = 0,
        )
        handler.invoke(Action.Click.OnDeleteDismiss)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnDeleteDismiss clears pending delete state`() {
        stateFlow.value = stateFlow.value.copy(
            pendingDeleteTarget = exerciseItem(),
            pendingDeleteImpact = 5,
            deleteImpactLoading = false,
        )
        handler.invoke(Action.Click.OnDeleteDismiss)
        assertEquals(null, stateFlow.value.pendingDeleteTarget)
        assertEquals(null, stateFlow.value.pendingDeleteImpact)
    }

    @Test
    fun `OnPermanentDeleteClick emits LongPress haptic and stores target`() {
        coEvery { interactor.countExerciseSessions(any()) } returns 0
        val captured = mutableListOf<Event>()

        val item = exerciseItem()
        handler.invoke(Action.Click.OnPermanentDeleteClick(item))

        assertEquals(item, stateFlow.value.pendingDeleteTarget)
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.first(), HapticFeedbackType.LongPress)
    }

    @Test
    fun `OnDeleteConfirm emits LongPress haptic and clears target`() {
        coEvery { interactor.permanentlyDeleteExercise(any()) } returns Unit
        stateFlow.value = stateFlow.value.copy(
            pendingDeleteTarget = exerciseItem(),
            pendingDeleteImpact = 2,
        )
        handler.invoke(Action.Click.OnDeleteConfirm)
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.first(), HapticFeedbackType.LongPress)
        assertEquals(null, stateFlow.value.pendingDeleteTarget)
    }

    @Test
    fun `OnDeleteConfirm without target does nothing`() {
        handler.invoke(Action.Click.OnDeleteConfirm)
        coVerify(exactly = 0) { interactor.permanentlyDeleteExercise(any()) }
        coVerify(exactly = 0) { interactor.permanentlyDeleteTraining(any()) }
    }

    private fun exerciseItem(): ArchivedItem.Exercise = ArchivedItem.Exercise(
        uuid = "uuid-x",
        name = "Bench",
        tags = emptyList(),
        archivedAt = 0L,
        type = io.github.stslex.workeeper.feature.settings.domain.model.ExerciseTypeDomain.WEIGHTED,
    )

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
