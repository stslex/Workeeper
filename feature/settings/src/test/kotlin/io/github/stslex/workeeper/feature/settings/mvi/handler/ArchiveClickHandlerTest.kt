// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.settings.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
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
import org.junit.jupiter.api.Test

internal class ArchiveClickHandlerTest {

    private val interactor = mockk<SettingsInteractor>(relaxed = true)
    private val emptyPaging: PagingUiState<androidx.paging.PagingData<ArchivedItem.Exercise>> =
        PagingUiState { kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty()) }
    private val emptyTrainingPaging: PagingUiState<androidx.paging.PagingData<ArchivedItem.Training>> =
        PagingUiState { kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty()) }

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
    fun `OnSegmentChange updates selectedSegment`() {
        handler.invoke(Action.Click.OnSegmentChange(Segment.TRAININGS))
        assertEquals(Segment.TRAININGS, stateFlow.value.selectedSegment)
    }

    @Test
    fun `OnSegmentChange to current segment is no-op`() {
        handler.invoke(Action.Click.OnSegmentChange(Segment.EXERCISES))
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
    fun `OnPermanentDeleteClick captures haptic event and target`() {
        coEvery { interactor.countExerciseSessions(any()) } returns 0
        val capturedEvent = slot<Event>()

        val item = exerciseItem()
        handler.invoke(Action.Click.OnPermanentDeleteClick(item))

        assertEquals(item, stateFlow.value.pendingDeleteTarget)
        verify { store.sendEvent(capture(capturedEvent)) }
        assert(capturedEvent.captured is Event.Haptic)
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
        type = io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel.WEIGHTED,
    )
}
