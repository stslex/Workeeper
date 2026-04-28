// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStore
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private fun setup(initialState: State = State.create("model")): TestSetup {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<ImageViewerHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        return TestSetup(stateFlow, store, CommonHandler(store))
    }

    private data class TestSetup(
        val stateFlow: MutableStateFlow<State>,
        val store: ImageViewerHandlerStore,
        val handler: CommonHandler,
    )

    @Test
    fun `Init is a no-op (state already from initial)`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Common.Init)
        verify(exactly = 0) { store.updateState(any()) }
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `TransformChange writes the absolute scale and offsets to State`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(
            Action.Common.TransformChange(scale = 2.5f, offsetX = 150f, offsetY = -200f),
        )
        assertEquals(2.5f, stateFlow.value.scale)
        assertEquals(150f, stateFlow.value.offsetX)
        assertEquals(-200f, stateFlow.value.offsetY)
    }
}
