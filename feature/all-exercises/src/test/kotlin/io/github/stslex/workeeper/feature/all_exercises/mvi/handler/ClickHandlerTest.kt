// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.mockk.CapturingSlot
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        blockedArchiveDialog = null,
    )
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<AllExercisesHandlerStore>(relaxed = true).apply {
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

    @Test
    fun `OnBulkDelete with selection opens confirm dialog`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(
                selectedUuids = persistentSetOf("uuid-1", "uuid-2"),
            ),
        )
        handler.invoke(Action.Click.OnBulkDelete)
        val pending = stateFlow.value.pendingBulkDelete
        assertNotNull(pending, "expected pendingBulkDelete to be set")
        assertEquals(2, pending!!.count)
    }

    @Test
    fun `OnBulkDeleteConfirm calls bulkArchive and clears selection on success`() = runTest {
        val targets = persistentSetOf("uuid-1", "uuid-2")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 2),
        )
        coEvery {
            interactor.bulkArchive(any())
        } returns BulkArchiveResult(archivedCount = 2, blocked = emptyList())

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
        coVerify { interactor.bulkArchive(setOf("uuid-1", "uuid-2")) }
        onSuccessSlot.captured(this, result)
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
        assertNull(stateFlow.value.pendingBulkDelete)
        // Pure success surfaces a snackbar, never the blocked dialog.
        assertNull(stateFlow.value.blockedArchiveDialog)
        verify(exactly = 1) { store.sendEvent(ofType<Event.ShowBulkDeleteSuccess>()) }
    }

    @Test
    fun `OnBulkDeleteConfirm opens blocked dialog and no snackbar when all blocked`() = runTest {
        val targets = persistentSetOf("uuid-1")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        coEvery { interactor.bulkArchive(any()) } returns BulkArchiveResult(
            archivedCount = 0,
            blocked = listOf(
                BulkArchiveResult.BlockedExerciseDomain(
                    name = "Bench",
                    activeTrainings = listOf("Push", "Legs"),
                ),
            ),
        )
        val (actionSlot, onSuccessSlot) = captureLaunch()

        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        onSuccessSlot.captured(this, actionSlot.captured(this))

        val dialog = stateFlow.value.blockedArchiveDialog
        assertNotNull(dialog)
        assertEquals(1, dialog?.items?.size)
        // Nothing archived → no "N archived" summary line.
        assertNull(dialog?.archivedSummary)
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
        assertNull(stateFlow.value.pendingBulkDelete)
        verify(exactly = 0) { store.sendEvent(ofType<Event.ShowBulkDeleteSuccess>()) }
    }

    @Test
    fun `OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block`() = runTest {
        val targets = persistentSetOf("uuid-1", "uuid-2")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 2),
        )
        coEvery { interactor.bulkArchive(any()) } returns BulkArchiveResult(
            archivedCount = 1,
            blocked = listOf(
                BulkArchiveResult.BlockedExerciseDomain(
                    name = "Bench",
                    activeTrainings = listOf("Push"),
                ),
            ),
        )
        val (actionSlot, onSuccessSlot) = captureLaunch()

        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        onSuccessSlot.captured(this, actionSlot.captured(this))

        val dialog = stateFlow.value.blockedArchiveDialog
        assertNotNull(dialog)
        assertEquals(1, dialog?.items?.size)
        // One exercise archived → summary line present (formatted via ResourceWrapper mock).
        assertNotNull(dialog?.archivedSummary)
        verify(exactly = 0) { store.sendEvent(ofType<Event.ShowBulkDeleteSuccess>()) }
    }

    private fun captureLaunch(): LaunchSlots {
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
        return LaunchSlots(actionSlot, onSuccessSlot)
    }

    private data class LaunchSlots(
        val action: CapturingSlot<suspend CoroutineScope.() -> BulkArchiveResult>,
        val onSuccess: CapturingSlot<suspend CoroutineScope.(BulkArchiveResult) -> Unit>,
    )

    @Test
    fun `OnBlockedArchiveDismiss clears the blocked dialog`() {
        stateFlow.value = stateFlow.value.copy(
            blockedArchiveDialog = State.BlockedArchiveDialog(
                archivedSummary = null,
                items = persistentListOf(),
            ),
        )
        handler.invoke(Action.Click.OnBlockedArchiveDismiss)
        assertNull(stateFlow.value.blockedArchiveDialog)
    }

    @Test
    fun `OnBulkDeleteDismiss clears pending`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        handler.invoke(Action.Click.OnBulkDeleteDismiss)
        assertNull(stateFlow.value.pendingBulkDelete)
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
