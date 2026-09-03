// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CommonHandlerTest {

    private fun setup(initialState: State = State.create("model", editable = true)): TestSetup {
        val store = FakeImageViewerHandlerStore(initialState)
        return TestSetup(store, CommonHandler(store))
    }

    private data class TestSetup(
        val store: FakeImageViewerHandlerStore,
        val handler: CommonHandler,
    )

    @Test
    fun `Init is a no-op because state is already from initial`() {
        val (store, handler) = setup()

        handler.invoke(Action.Common.Init)

        assertEquals(State.create("model", editable = true), store.state.value)
        assertEquals(0, store.stateUpdateCount)
        assertEquals(emptyList(), store.events)
        assertEquals(emptyList(), store.consumedActions)
    }

    @Test
    fun `TransformChange writes the absolute scale and offsets to State`() {
        val (store, handler) = setup()

        handler.invoke(
            Action.Common.TransformChange(scale = 2.5f, offsetX = 150f, offsetY = -200f),
        )

        assertEquals(2.5f, store.state.value.scale)
        assertEquals(150f, store.state.value.offsetX)
        assertEquals(-200f, store.state.value.offsetY)
        assertEquals(1, store.stateUpdateCount)
        assertEquals(emptyList(), store.events)
        assertEquals(emptyList(), store.consumedActions)
    }
}
