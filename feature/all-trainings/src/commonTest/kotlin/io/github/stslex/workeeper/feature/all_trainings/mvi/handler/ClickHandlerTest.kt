// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClickHandlerTest {

    private val interactor = RecordingAllTrainingsInteractor()
    private val emptyPaging = PagingUiState { flowOf(PagingData.empty<TrainingListItemUi>()) }
    private val initialState = State(
        pagingUiState = emptyPaging,
        availableTags = persistentListOf(),
        activeTagFilter = persistentSetOf(),
        selectionMode = State.SelectionMode.Off,
        pendingBulkDelete = null,
        hasActiveSession = false,
    )

    private val store = FakeAllTrainingsHandlerStore(initialState)

    private val stateFlow = store.mutableState

    private val handler = ClickHandler(interactor, store)

    @Test
    fun `OnTrainingClick emits haptic and navigates to OpenDetail`() {
        handler.invoke(Action.Click.OnTrainingClick("uuid-1"))
        assertHaptic(lastSentEvent(), HapticFeedbackType.ContextClick)
        assertContains(store.consumed, Action.Navigation.OpenDetail("uuid-1"))
    }

    @Test
    fun `OnFabClick emits haptic and navigates to OpenCreate`() {
        handler.invoke(Action.Click.OnFabClick)
        assertHaptic(lastSentEvent(), HapticFeedbackType.ContextClick)
        assertContains(store.consumed, Action.Navigation.OpenCreate)
    }

    /** §26 "Haptics": the FAB morph fires nothing — the long press that entered selection did. */
    @Test
    fun `OnFabClick with selection fires no haptic and sets pendingBulkDelete`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(
                selectedUuids = persistentSetOf("uuid-1", "uuid-2"),
            ),
        )
        handler.invoke(Action.Click.OnFabClick)
        assertEquals(emptyList(), store.events)
        assertEquals(emptyList(), store.consumed)
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
        interactor.archiveResult = BulkArchiveResult(archivedCount = 2, blockedNames = emptyList())

        handler.invoke(Action.Click.OnBulkDeleteConfirm)

        // The recorded launch runs its action and hands the result to its onSuccess.
        store.launches.last().invoke(this)
        assertContains(interactor.archiveCalls, setOf("uuid-1", "uuid-2"))
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
        assertNull(stateFlow.value.pendingBulkDelete)
        // D1: the success event is semantic; the graph resolves its copy.
        assertEquals(
            Event.ShowBulkDeleteSuccess(archivedCount = 2, blockedNames = persistentListOf()),
            store.events.last(),
        )
    }

    @Test
    fun `OnBulkDeleteDismiss clears pending delete`() {
        stateFlow.value =
            stateFlow.value.copy(pendingBulkDelete = State.PendingBulkDelete(count = 2))
        handler.invoke(Action.Click.OnBulkDeleteDismiss)
        assertEquals(null, stateFlow.value.pendingBulkDelete)
    }

    /** At least one event was sent; the last one is the one a capturing slot would hold. */
    private fun lastSentEvent(): Event {
        assertTrue(store.events.isNotEmpty(), "expected at least one sendEvent")
        return store.events.last()
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.HapticClick, "expected Event.HapticClick but got $event")
        assertEquals(expected, (event as Event.HapticClick).type)
    }

    /** §26 "Haptics": each case asserts the constant, not merely that something fired. */
    @Test
    fun `entering selection by long press fires LongPress`() {
        handler.invoke(Action.Click.OnTrainingLongPress("uuid-1"))
        assertHaptic(lastSentEvent(), HapticFeedbackType.LongPress)
    }

    @Test
    fun `toggling an item inside selection fires ContextClick not LongPress`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnSelectionToggle("uuid-2"))
        assertHaptic(lastSentEvent(), HapticFeedbackType.ContextClick)
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
        assertHaptic(lastSentEvent(), HapticFeedbackType.ContextClick)
    }

    /** A long press inside selection is a toggle: ContextClick, not a second LongPress. */
    @Test
    fun `long press inside selection fires ContextClick not a second LongPress`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnTrainingLongPress("uuid-2"))
        assertEquals(1, store.events.size)
        assertHaptic(store.events.single(), HapticFeedbackType.ContextClick)
    }

    /** The tag chip is not the nav bar: `SegmentTick` is that surface's, and fires nowhere here. */
    @Test
    fun `toggling a tag filter fires no haptic`() {
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        assertEquals(emptyList(), store.events)
    }

    /** `Confirm` fires after the dialog, not on the button that opens it. */
    @Test
    fun `confirmed bulk archive fires Confirm`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        assertTrue(
            store.events.any { it is Event.HapticClick && it.type == HapticFeedbackType.Confirm },
            "expected Confirm after the dialog's confirm",
        )
    }

    /** The filtered-to-empty state's only action: one tap, and no haptic. */
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
        assertEquals(emptyList(), store.events)
    }

    /** Guarded, so a redundant emit cannot restart the paging flow the filter feeds. */
    @Test
    fun `OnClearTagFilter on an already-empty filter changes nothing`() {
        val before = stateFlow.value
        handler.invoke(Action.Click.OnClearTagFilter)
        assertEquals(before, stateFlow.value)
        assertEquals(0, store.updateStateCalls)
    }
    /** The empty state's CTA opens create and fires nothing; only the FAB fires ContextClick. */
    @Test
    fun `OnEmptyCreate opens create and fires no haptic`() {
        handler.invoke(Action.Click.OnEmptyCreate)
        assertContains(store.consumed, Action.Navigation.OpenCreate)
        assertEquals(emptyList(), store.events)
    }
}

/**
 * The handler's store as the relaxed MockK store answered it: [updateState] and the update-lambda
 * [updateStateImmediate] apply to [mutableState], [sendEvent] and [consume] are recorded, and each
 * [launch] is recorded without running, as one closure that runs its action and hands the result
 * to its onSuccess. Members [ClickHandler] never reaches fail fast.
 */
private class FakeAllTrainingsHandlerStore(
    initialState: State,
) : AllTrainingsHandlerStore {

    val mutableState = MutableStateFlow(initialState)
    val events = mutableListOf<Event>()
    val consumed = mutableListOf<Action>()
    val launches = mutableListOf<suspend (CoroutineScope) -> Unit>()

    var updateStateCalls: Int = 0
        private set

    override val state: StateFlow<State> = mutableState

    override val lastAction: Action?
        get() = error("lastAction must not be read by ClickHandler")

    override val logger: Logger
        get() = error("logger must not be read by ClickHandler")

    override fun sendEvent(event: Event) {
        events.add(event)
    }

    override fun consume(action: Action) {
        consumed.add(action)
    }

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by ClickHandler")

    override fun updateState(update: (State) -> State) {
        updateStateCalls += 1
        mutableState.value = update(mutableState.value)
    }

    override suspend fun updateStateImmediate(update: suspend (State) -> State) {
        mutableState.value = update(mutableState.value)
    }

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by ClickHandler")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job {
        launches.add { scope -> scope.onSuccess(scope.action()) }
        return Job()
    }

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launchDefault must not be used by ClickHandler")

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = error("Flow.launch must not be used by ClickHandler")
}

/**
 * Records [archiveTrainings] and answers it with [archiveResult]; the other suspend operations
 * return the relaxed defaults, and the flows [ClickHandler] never collects fail fast.
 */
private class RecordingAllTrainingsInteractor : AllTrainingsInteractor {

    val archiveCalls = mutableListOf<Set<String>>()
    var archiveResult = BulkArchiveResult(archivedCount = 0, blockedNames = emptyList())

    override fun observeTrainings(filterTagUuids: Set<String>): Nothing =
        error("observeTrainings must not be used by ClickHandler")

    override fun observeAvailableTags(): Nothing =
        error("observeAvailableTags must not be used by ClickHandler")

    override fun observeHasActiveSession(): Nothing =
        error("observeHasActiveSession must not be used by ClickHandler")

    override suspend fun archiveTrainings(uuids: Set<String>): BulkArchiveResult {
        archiveCalls.add(uuids)
        return archiveResult
    }

    override suspend fun deleteTrainings(uuids: Set<String>): Int = 0

    override suspend fun canPermanentlyDelete(uuids: Set<String>): Boolean = false
}
