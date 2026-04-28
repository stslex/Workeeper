// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `TimerTick updates nowMillis when session is started`() {
        val stateFlow = MutableStateFlow(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1").copy(
                startedAt = 1L,
                nowMillis = 1L,
                isLoading = false,
            ),
        )
        val store = mockk<LiveWorkoutHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = store,
        )

        handler.invoke(Action.Common.TimerTick)

        assertTrue(stateFlow.value.nowMillis >= stateFlow.value.startedAt)
    }
}
