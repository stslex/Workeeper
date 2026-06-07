// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<AllTrainingsInteractor>(relaxed = true)
    private val emptyPaging = PagingUiState { flowOf(PagingData.empty<TrainingListItemUi>()) }
    private val initialState = State(
        pagingUiState = emptyPaging,
        availableTags = persistentListOf(),
        activeTagFilter = persistentSetOf(),
        selectionMode = State.SelectionMode.Off,
        pendingBulkDelete = null,
    )
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<AllTrainingsHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        coEvery { updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every {
            launch<Any>(
                any(),
                any(),
                any(),
                any(),
                any<suspend CoroutineScope.() -> Any>(),
            )
        } answers { mockk(relaxed = true) }
    }

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    private val handler = ClickHandler(interactor, resourceWrapper, store)

    @Test
    fun `OnTrainingClick emits haptic and navigates to OpenDetail`() {
        handler.invoke(Action.Click.OnTrainingClick("uuid-1"))
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
    fun `OnFabClick with selection emits LongPress haptic and sets pendingBulkDelete`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(
                selectedUuids = persistentSetOf("uuid-1", "uuid-2"),
            ),
        )
        handler.invoke(Action.Click.OnFabClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.LongPress)
        verify(exactly = 0) { store.consume(any()) }
        assertEquals(2, stateFlow.value.pendingBulkDelete?.count)
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
    fun `OnSelectionExit clears selection mode`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnSelectionExit)
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
    }

    @Test
    fun `OnBulkDeleteConfirm calls archiveTrainings and clears selection on success`() = runTest {
        val targets = persistentSetOf("uuid-1", "uuid-2")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 2),
        )
        coEvery {
            interactor.archiveTrainings(any())
        } returns BulkArchiveResult(archivedCount = 2, blockedNames = emptyList())

        val actionSlot = slot<suspend CoroutineScope.() -> BulkArchiveResult>()
        val onSuccessSlot = slot<suspend CoroutineScope.(BulkArchiveResult) -> Unit>()
        every {
            store.launch(
                onError = any(),
                onSuccess = capture(onSuccessSlot),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = capture(actionSlot),
            )
        } returns mockk(relaxed = true)

        handler.invoke(Action.Click.OnBulkDeleteConfirm)

        val result = actionSlot.captured(this)
        coVerify { interactor.archiveTrainings(setOf("uuid-1", "uuid-2")) }
        onSuccessSlot.captured(this, result)
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
        assertNull(stateFlow.value.pendingBulkDelete)
    }

    @Test
    fun `OnBulkDeleteDismiss clears pending delete`() {
        stateFlow.value =
            stateFlow.value.copy(pendingBulkDelete = State.PendingBulkDelete(count = 2))
        handler.invoke(Action.Click.OnBulkDeleteDismiss)
        assertEquals(null, stateFlow.value.pendingBulkDelete)
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.HapticClick, "expected Event.HapticClick but got $event")
        assertEquals(expected, (event as Event.HapticClick).type)
    }
}
