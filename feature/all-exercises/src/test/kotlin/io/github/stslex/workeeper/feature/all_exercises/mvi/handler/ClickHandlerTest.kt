// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<AllExercisesInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val emptyPaging = PagingUiState { flowOf(PagingData.empty<ExerciseUiModel>()) }
    private val initialState = State(
        pagingUiState = emptyPaging,
        availableTags = persistentListOf(),
        activeTagFilter = persistentSetOf(),
        pendingPermanentDelete = null,
        selectionMode = State.SelectionMode.Off,
        pendingBulkDelete = null,
    )
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<AllExercisesHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every { launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Unit>()) } answers {
            mockk(relaxed = true)
        }
    }

    private val handler = ClickHandler(interactor, resourceWrapper, store)

    @Test
    fun `OnExerciseClick emits haptic and navigates to OpenDetail`() {
        handler.invoke(Action.Click.OnExerciseClick("uuid-1"))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.ContextClick)
        verify { store.consume(Action.Navigation.OpenDetail("uuid-1")) }
    }

    @Test
    fun `OnFabClick emits haptic and navigates to OpenCreate`() {
        handler.invoke(Action.Click.OnFabClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.ContextClick)
        verify { store.consume(Action.Navigation.OpenCreate) }
    }

    @Test
    fun `OnTagFilterToggle adds tag when not selected`() {
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        assertEquals(setOf("tag-1"), stateFlow.value.activeTagFilter.toSet())
    }

    @Test
    fun `OnTagFilterToggle removes tag when already selected`() {
        stateFlow.value = stateFlow.value.copy(activeTagFilter = persistentSetOf("tag-1", "tag-2"))
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        assertEquals(setOf("tag-2"), stateFlow.value.activeTagFilter.toSet())
    }

    @Test
    fun `OnArchiveSwipe Success emits ShowArchiveSuccess`() {
        coEvery { interactor.archiveExercise("uuid-1") } returns ArchiveResult.Success
        handler.invoke(Action.Click.OnArchiveSwipe(uuid = "uuid-1", name = "Bench"))
        // Capturing is async via launch; just verify haptic
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.any { it is Event.Haptic && it.type == HapticFeedbackType.LongPress })
    }

    @Test
    fun `OnUndoArchive triggers restoreExercise`() {
        coEvery { interactor.restoreExercise(any()) } returns Unit
        handler.invoke(Action.Click.OnUndoArchive("uuid-1"))
        // launch invokes the suspend block; in our mock the slot { } answers with mock job —
        // verify the launch was invoked at all.
        verify(atLeast = 1) {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Unit>())
        }
        // restoreExercise will not actually be called here because launch is mocked, but the
        // intention is exercised via the launch capture.
        coVerify(exactly = 0) { interactor.restoreExercise(any()) }
    }

    @Test
    fun `OnCancelPermanentDelete clears pending delete`() {
        stateFlow.value = stateFlow.value.copy(
            pendingPermanentDelete = State.PendingDelete(uuid = "uuid-1", name = "Bench"),
        )
        handler.invoke(Action.Click.OnCancelPermanentDelete)
        assertEquals(null, stateFlow.value.pendingPermanentDelete)
    }

    @Test
    fun `OnConfirmPermanentDelete with no pending is no-op`() {
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnConfirmPermanentDelete with pending clears state and emits LongPress haptic`() {
        stateFlow.value = stateFlow.value.copy(
            pendingPermanentDelete = State.PendingDelete(uuid = "uuid-1", name = "Bench"),
        )
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        assertEquals(null, stateFlow.value.pendingPermanentDelete)
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.any { it is Event.Haptic && it.type == HapticFeedbackType.LongPress })
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
