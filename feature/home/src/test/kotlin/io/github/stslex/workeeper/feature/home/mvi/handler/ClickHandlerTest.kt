// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val baseState = State.INITIAL.copy(isLoading = false)

    @Test
    fun `OnSettingsClick consumes OpenSettings navigation and emits HapticClick`() {
        val store = newStore(baseState)
        val handler = ClickHandler(store)

        handler.invoke(Action.Click.OnSettingsClick)

        verify(exactly = 1) { store.consume(Action.Navigation.OpenSettings) }
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(true, captured.captured is Event.HapticClick)
    }

    @Test
    fun `OnActiveSessionClick is a no-op when no active session`() {
        val store = newStore(baseState)
        val handler = ClickHandler(store)

        handler.invoke(Action.Click.OnActiveSessionClick)

        verify(exactly = 0) { store.consume(any()) }
    }

    @Test
    fun `OnActiveSessionClick consumes OpenLiveWorkout with the session uuid`() {
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
        val handler = ClickHandler(store)

        handler.invoke(Action.Click.OnActiveSessionClick)

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenLiveWorkout(sessionUuid = "session-7"))
        }
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.HapticClick).type)
    }

    private fun newStore(state: State): HomeHandlerStore {
        val flow = MutableStateFlow(state)
        return mockk(relaxed = true) {
            every { this@mockk.state } returns flow
        }
    }
}
