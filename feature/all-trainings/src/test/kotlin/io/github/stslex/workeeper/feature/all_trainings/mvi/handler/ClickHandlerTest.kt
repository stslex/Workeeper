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

    /**
     * §26 "Haptics": **the FAB morph fires nothing.** It follows the long press that already fired
     * when selection was entered, and two in a row read as a fault. This test asserted the opposite
     * — it encoded the behaviour the ledger has since retracted, so it inverts with it.
     */
    @Test
    fun `OnFabClick with selection fires no haptic and sets pendingBulkDelete`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(
                selectedUuids = persistentSetOf("uuid-1", "uuid-2"),
            ),
        )
        handler.invoke(Action.Click.OnFabClick)
        verify(exactly = 0) { store.sendEvent(any()) }
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

    /**
     * §26 "Haptics", all three, each asserted on the constant rather than on "a haptic fired".
     * The vocabulary is the decision — `LongPress` for entering the mode, `ContextClick` for
     * changing what is in it, `Confirm` for the act itself — so a test that only checked *that*
     * something buzzed would pass while the meaning drifted.
     */
    @Test
    fun `entering selection by long press fires LongPress`() {
        handler.invoke(Action.Click.OnTrainingLongPress("uuid-1"))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.LongPress)
    }

    @Test
    fun `toggling an item inside selection fires ContextClick, not LongPress`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnSelectionToggle("uuid-2"))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.ContextClick)
    }

    /** Untoggle is the same gesture in the other direction and carries the same constant. */
    @Test
    fun `untoggling an item inside selection fires ContextClick`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(
                selectedUuids = persistentSetOf("uuid-1", "uuid-2"),
            ),
        )
        handler.invoke(Action.Click.OnSelectionToggle("uuid-2"))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.ContextClick)
    }

    /**
     * A long press on a row while selection is already on is a **toggle**, so it gets
     * ContextClick and not a second LongPress: two in a row read as a fault.
     */
    @Test
    fun `long press inside selection fires ContextClick, not a second LongPress`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnTrainingLongPress("uuid-2"))
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.ContextClick)
    }

    /** The tag chip is not the nav bar: `SegmentTick` is that surface's, and fires nowhere here. */
    @Test
    fun `toggling a tag filter fires no haptic`() {
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    /**
     * `Confirm` for the act itself — after the dialog, not on the button that opens it.
     *
     * This assertion was promised by the KDoc above and missing from the file: mutation #11 of the
     * all-exercises delta (`Confirm` reverted to `LongPress`) reddened that screen's suite and
     * passed silently here.
     */
    @Test
    fun `confirmed bulk archive fires Confirm`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        val captured = mutableListOf<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(
            captured.any { it is Event.HapticClick && it.type == HapticFeedbackType.Confirm },
            "expected Confirm after the dialog's confirm",
        )
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
