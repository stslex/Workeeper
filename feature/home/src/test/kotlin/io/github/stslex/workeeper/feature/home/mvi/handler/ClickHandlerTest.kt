// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val baseState = State.INITIAL.copy(isActiveLoaded = true, isRecentLoaded = true)
    private val interactor = mockk<HomeInteractor>(relaxed = true)
    private val resources = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `OnSettingsClick consumes OpenSettings navigation and emits HapticClick`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnSettingsClick)

        verify(exactly = 1) { store.consume(Action.Navigation.OpenSettings) }
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(true, captured.captured is Event.HapticClick)
    }

    @Test
    fun `OnActiveSessionClick is a no-op when no active session`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnActiveSessionClick)

        verify(exactly = 0) { store.consume(any()) }
    }

    @Test
    fun `OnActiveSessionClick consumes OpenLiveWorkoutResume with the session uuid`() {
        val active = State.ActiveSessionInfo(
            sessionUuid = "session-7",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            startedAt = 0L,
            doneCount = 1,
            totalCount = 3,
            elapsedDurationLabel = "00:10",
        )
        val store = newStore(baseState.copy(activeSession = active))
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnActiveSessionClick)

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-7"))
        }
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnRecentSessionClick consumes OpenPastSession with the session uuid`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnRecentSessionClick(sessionUuid = "session-22"))

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenPastSession(sessionUuid = "session-22"))
        }
    }

    @Test
    fun `OnPickerSeeAllClick hides picker and consumes OpenAllTrainings`() {
        val visiblePicker = State.PickerState.Visible(
            templates = kotlinx.collections.immutable.persistentListOf(),
            isLoading = false,
        )
        val store = newStore(baseState.copy(picker = visiblePicker))
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnPickerSeeAllClick)

        verify(exactly = 1) { store.consume(Action.Navigation.OpenAllTrainings) }
    }

    @Test
    fun `OnPickerTrainingSelected hides the picker before resolving conflict`() {
        val visiblePicker = State.PickerState.Visible(
            templates = kotlinx.collections.immutable.persistentListOf(),
            isLoading = false,
        )
        val flow = MutableStateFlow(baseState.copy(picker = visiblePicker))
        val store = mockk<HomeHandlerStore>(relaxed = true) {
            every { this@mockk.state } returns flow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                flow.value = update(flow.value)
            }
            every { scope } returns AppCoroutineScope(
                scope = TestScope(),
                defaultDispatcher = Dispatchers.Unconfined,
                immediateDispatcher = Dispatchers.Unconfined,
            )
        }
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnPickerTrainingSelected(trainingUuid = "tpl-1"))

        assertEquals(State.PickerState.Hidden, flow.value.picker)
    }

    @Test
    fun `OnConflictDismiss clears pendingConflict`() {
        val state = baseState.copy(
            pendingConflict = State.ConflictInfo(
                activeSessionUuid = "session-1",
                requestedTrainingUuid = "tpl-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        val flow = MutableStateFlow(state)
        val store = mockk<HomeHandlerStore>(relaxed = true) {
            every { this@mockk.state } returns flow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                flow.value = update(flow.value)
            }
            every { scope } returns AppCoroutineScope(
                scope = TestScope(),
                defaultDispatcher = Dispatchers.Unconfined,
                immediateDispatcher = Dispatchers.Unconfined,
            )
        }
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnConflictDismiss)

        assertEquals(null, flow.value.pendingConflict)
    }

    @Test
    fun `OnConflictResume consumes OpenLiveWorkoutResume with the active session uuid`() {
        val state = baseState.copy(
            pendingConflict = State.ConflictInfo(
                activeSessionUuid = "session-1",
                requestedTrainingUuid = "tpl-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        val flow = MutableStateFlow(state)
        val store = mockk<HomeHandlerStore>(relaxed = true) {
            every { this@mockk.state } returns flow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                flow.value = update(flow.value)
            }
            every { scope } returns AppCoroutineScope(
                scope = TestScope(),
                defaultDispatcher = Dispatchers.Unconfined,
                immediateDispatcher = Dispatchers.Unconfined,
            )
        }
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnConflictResume)

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-1"))
        }
        assertEquals(null, flow.value.pendingConflict)
    }

    private fun newStore(state: State): HomeHandlerStore {
        val flow = MutableStateFlow(state)
        return mockk(relaxed = true) {
            every { this@mockk.state } returns flow
            every { scope } returns AppCoroutineScope(
                scope = TestScope(),
                defaultDispatcher = Dispatchers.Unconfined,
                immediateDispatcher = Dispatchers.Unconfined,
            )
        }
    }
}
