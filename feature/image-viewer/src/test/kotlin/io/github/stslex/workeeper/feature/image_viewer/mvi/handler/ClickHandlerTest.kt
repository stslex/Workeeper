// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.navigation.Screen.ExerciseImageRequest
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

    private fun setup(
        initialState: State = State.create("model", editable = true),
    ): TestSetup {
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

    /**
     * The verbs the viewer carries are a REQUEST to the caller, and only a caller that can honour
     * one may be offered it. The exercise DETAIL screen opens this same route and has no Save and
     * no dirty interception, so a replace staged from there would look applied and be lost on the
     * way out — worse than no affordance, because it reads as having worked.
     *
     * The `⋮` is hidden on a non-editable route, so this branch is unreachable through the UI; it
     * is guarded anyway because the alternative is a representable state (`Menu` on a route whose
     * caller cannot save) defended only by a composable remembering to check a flag.
     */
    @Test
    fun `a non-editable route cannot open the verbs sheet`() {
        val (stateFlow, store, handler) = setup(State.create("model", editable = false))

        handler.invoke(Action.Click.OnMenuClick)

        assertEquals(State.SheetState.Hidden, stateFlow.value.sheetState)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    /** The other direction, so the guard is a gate and not an off switch. */
    @Test
    fun `an editable route opens the verbs sheet`() {
        val (stateFlow, _, handler) = setup()

        handler.invoke(Action.Click.OnMenuClick)

        assertEquals(State.SheetState.Menu, stateFlow.value.sheetState)
    }

    @Test
    fun `Replace pops with a REPLACE request and closes the sheet in the same transition`() {
        val (stateFlow, store, handler) = setup(
            State.create("model", editable = true).copy(sheetState = State.SheetState.Menu),
        )

        handler.invoke(Action.Click.OnReplaceClick)

        assertEquals(State.SheetState.Hidden, stateFlow.value.sheetState)
        verify(exactly = 1) {
            store.consume(
                Action.Navigation.BackWithRequest(ExerciseImageRequest.REPLACE),
            )
        }
    }

    @Test
    fun `Remove pops with a REMOVE request`() {
        val (_, store, handler) = setup(
            State.create("model", editable = true).copy(sheetState = State.SheetState.Menu),
        )

        handler.invoke(Action.Click.OnRemoveClick)

        verify(exactly = 1) {
            store.consume(
                Action.Navigation.BackWithRequest(ExerciseImageRequest.REMOVE),
            )
        }
    }

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
            State.create("model", editable = true).copy(scale = State.MIN_SCALE, offsetX = 0f, offsetY = 0f),
        )
        handler.invoke(Action.Click.OnDoubleTap)
        assertEquals(State.DOUBLE_TAP_TARGET_SCALE, stateFlow.value.scale)
        assertEquals(0f, stateFlow.value.offsetX)
        assertEquals(0f, stateFlow.value.offsetY)
    }

    @Test
    fun `OnDoubleTap from any scale above MIN_SCALE collapses to MIN_SCALE and resets offsets`() {
        val (stateFlow, _, handler) = setup(
            State.create("model", editable = true).copy(scale = 3f, offsetX = 100f, offsetY = -40f),
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
