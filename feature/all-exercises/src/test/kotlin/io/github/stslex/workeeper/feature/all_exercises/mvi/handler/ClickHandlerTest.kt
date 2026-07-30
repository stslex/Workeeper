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

    /**
     * §26 "Haptics" gives `Confirm` to "подтверждённое удаление, **после диалога**, а не по нажатию
     * кнопки" — so the buzz after this dialog's confirm is `Confirm`, not `LongPress`. The test
     * asserted `LongPress` because that is what the code did; inverting it with the ledger rather
     * than deleting it keeps the site gated.
     *
     * The path itself is unreachable in production (B23: nothing writes `pendingPermanentDelete` to
     * non-null). This test constructs the state by hand, which is exactly why it passed on a path
     * production cannot enter — worth knowing when reading it.
     */
    @Test
    fun `OnConfirmPermanentDelete with pending clears state and emits Confirm haptic`() {
        stateFlow.value = stateFlow.value.copy(
            pendingPermanentDelete = State.PendingDelete(uuid = "uuid-1", name = "Bench"),
        )
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        assertEquals(null, stateFlow.value.pendingPermanentDelete)
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.any { it is Event.Haptic && it.type == HapticFeedbackType.Confirm })
    }

    /**
     * The FAB morph fires **nothing** (§26 "Haptics": it follows the long press that already fired,
     * and two in a row read as a fault). The screen's selection top bar routes its archive action
     * to the same handler, so this covers both affordances.
     */
    @Test
    fun `OnBulkDelete fires no haptic — the morph is silent`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnBulkDelete)
        val captured = mutableListOf<Event>()
        verify(exactly = 0) { store.sendEvent(capture(captured)) }
    }

    /** No haptic on a filter chip: `SegmentTick` belongs to the nav bar's tab change. */
    @Test
    fun `OnTagFilterToggle fires no haptic`() {
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    /** The confirmed archive is where `Confirm` fires — after the dialog, not on the FAB. */
    @Test
    fun `OnBulkDeleteConfirm emits Confirm haptic`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.any { it is Event.Haptic && it.type == HapticFeedbackType.Confirm })
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
    /**
     * The filtered-to-empty state's only action, and it is one tap rather than N.
     *
     * No haptic: [ClickHandler] fires none on a filter change and the vocabulary is four constants,
     * none of which is "a filter changed". Asserted rather than assumed — silence is also what an
     * accidental deletion produces.
     */
    @Test
    fun `OnClearTagFilter empties the whole filter in one act`() {
        stateFlow.value = stateFlow.value.copy(
            activeTagFilter = persistentSetOf("tag-1", "tag-2", "tag-3"),
        )
        handler.invoke(Action.Click.OnClearTagFilter)
        assertEquals(emptySet<String>(), stateFlow.value.activeTagFilter.toSet())
    }

    @Test
    fun `OnClearTagFilter fires no haptic`() {
        stateFlow.value = stateFlow.value.copy(activeTagFilter = persistentSetOf("tag-1"))
        handler.invoke(Action.Click.OnClearTagFilter)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    /** Guarded, so a redundant emit cannot restart the paging flow the filter feeds. */
    @Test
    fun `OnClearTagFilter on an already-empty filter changes nothing`() {
        val before = stateFlow.value
        handler.invoke(Action.Click.OnClearTagFilter)
        assertEquals(before, stateFlow.value)
        verify(exactly = 0) { store.updateState(any()) }
    }
    /**
     * Entering selection is the gesture `LongPress` is for. Exactly one haptic.
     */
    @Test
    fun `entering selection by long press fires LongPress`() {
        handler.invoke(Action.Click.OnExerciseLongPress("uuid-1"))
        val captured = mutableListOf<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertTrue(captured.single().let { it is Event.Haptic && it.type == HapticFeedbackType.LongPress })
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.On)
    }

    /**
     * The defect this file exists to pin, and it shipped: a long press **inside** selection is a
     * toggle, so it gets `ContextClick` and nothing else. The handler used to fire `LongPress`
     * first and then delegate, putting two haptics on one gesture — where the sibling screen, whose
     * fix predates this one, fires exactly one. `exactly = 1` is the whole assertion; a
     * `captured.any { … }` check would have passed on the broken code.
     */
    @Test
    fun `long press inside selection fires ContextClick, not a second LongPress`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        val captured = mutableListOf<Event>()
        handler.invoke(Action.Click.OnExerciseLongPress("uuid-2"))
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(
            HapticFeedbackType.ContextClick,
            (captured.single() as Event.Haptic).type,
        )
    }

    /** Toggling what is in the selection is `ContextClick`, once. */
    @Test
    fun `selection toggle fires ContextClick`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        val captured = mutableListOf<Event>()
        handler.invoke(Action.Click.OnSelectionToggle("uuid-2"))
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.ContextClick, (captured.single() as Event.Haptic).type)
    }

    /** Leaving the mode is `ContextClick` too — it changes the selection to nothing. */
    @Test
    fun `selection exit fires ContextClick`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        val captured = mutableListOf<Event>()
        handler.invoke(Action.Click.OnSelectionExit)
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.ContextClick, (captured.single() as Event.Haptic).type)
    }
    /**
     * The empty state's CTA opens create and fires **nothing**. The FAB fires `ContextClick`; a
     * button inside an empty state does not, and routing the CTA through [Action.Click.OnFabClick]
     * gave this screen a haptic its sibling's identical `.empty` button does not have.
     *
     * **Residual, stated rather than papered over:** this asserts the *handler*. That the screen
     * dispatches this action and not `OnFabClick` is screen wiring, which no unit test and no
     * golden can see — a Compose UI test could, but `ui_tests.yml` is `workflow_dispatch`-only and
     * does not gate PRs. Proven by mutation to be uncovered, and left named. Same class as the
     * paging-tail selector before it was extracted.
     */
    @Test
    fun `OnEmptyCreate opens create and fires no haptic`() {
        handler.invoke(Action.Click.OnEmptyCreate)
        verify { store.consume(Action.Navigation.OpenCreate) }
        verify(exactly = 0) { store.sendEvent(any()) }
    }
}
