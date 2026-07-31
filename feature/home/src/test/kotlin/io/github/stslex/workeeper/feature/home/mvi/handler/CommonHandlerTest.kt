// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.emptyPagingState
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
    }
    private val resources = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `Init subscribes to the active session and NOT to the paged recent list`() {
        val stateFlow = MutableStateFlow(emptyPagingState())
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
        // `Init` no longer subscribes to the recent list at all: it is paged, and its flow is
        // built once in `PagingHandler.pagingUiState` and collected by the screen. Asserted as an
        // absence deliberately — the old case verified a call this handler must NOT make now.
        verify(exactly = 0) { interactor.pagedRecent() }
        assertEquals(emptyPagingState(), stateFlow.value)
    }
}
