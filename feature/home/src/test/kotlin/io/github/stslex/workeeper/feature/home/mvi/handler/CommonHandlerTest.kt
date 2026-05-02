// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<HomeInteractor>(relaxed = true) {
        every { observeActiveSession() } returns emptyFlow()
        every { observeRecent(any()) } returns emptyFlow()
    }
    private val resources = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `Init subscribes to active session and recent flows`() {
        val stateFlow = MutableStateFlow(State.INITIAL)
        val store = mockk<HomeHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            store = store,
        )

        handler.invoke(Action.Common.Init)

        verify { interactor.observeActiveSession() }
        verify { interactor.observeRecent(any()) }
        assertEquals(State.INITIAL, stateFlow.value)
    }
}
