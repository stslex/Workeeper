// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStore
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private fun setup(initialState: State = State.create("model")): TestSetup {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<ImageViewerHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        return TestSetup(stateFlow, store, ClickHandler(store))
    }

    private data class TestSetup(
        val stateFlow: MutableStateFlow<State>,
        val store: ImageViewerHandlerStore,
        val handler: ClickHandler,
    )

    @Test
    fun `OnBackClick emits ContextClick haptic and consumes Navigation Back`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnBackClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.HapticClick)
        assertEquals(
            HapticFeedbackType.ContextClick,
            (captured.captured as Event.HapticClick).type,
        )
        verify { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnDoubleTap from MIN_SCALE jumps to DOUBLE_TAP_TARGET_SCALE and resets offsets`() {
        val (stateFlow, _, handler) = setup(
            State.create("model").copy(scale = State.MIN_SCALE, offsetX = 0f, offsetY = 0f),
        )
        handler.invoke(Action.Click.OnDoubleTap)
        assertEquals(State.DOUBLE_TAP_TARGET_SCALE, stateFlow.value.scale)
        assertEquals(0f, stateFlow.value.offsetX)
        assertEquals(0f, stateFlow.value.offsetY)
    }

    @Test
    fun `OnDoubleTap from any scale above MIN_SCALE collapses to MIN_SCALE and resets offsets`() {
        val (stateFlow, _, handler) = setup(
            State.create("model").copy(scale = 3f, offsetX = 100f, offsetY = -40f),
        )
        handler.invoke(Action.Click.OnDoubleTap)
        assertEquals(State.MIN_SCALE, stateFlow.value.scale)
        assertEquals(0f, stateFlow.value.offsetX)
        assertEquals(0f, stateFlow.value.offsetY)
    }

    @Test
    fun `OnDoubleTap emits ContextClick haptic`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnDoubleTap)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.HapticClick)
    }
}
