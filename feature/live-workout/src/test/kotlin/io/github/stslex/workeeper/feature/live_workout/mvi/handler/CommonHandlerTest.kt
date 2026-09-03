// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    /**
     * The store mock runs `launch` synchronously and routes a throw to `onError`, because that
     * routing is the thing under test — a mock that swallowed it would report the defect fixed.
     */
    private fun setup(
        initialState: State,
    ): Triple<MutableStateFlow<State>, CommonHandler, LiveWorkoutHandlerStore> {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<LiveWorkoutHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
            coEvery { updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
                val update = firstArg<suspend (State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
            every { launch<Any?>(any(), any(), any(), any(), any()) } answers {
                val onError = firstArg<suspend (Throwable) -> Unit>()
                val onSuccess = secondArg<suspend CoroutineScope.(Any?) -> Unit>()
                val action = arg<suspend CoroutineScope.() -> Any?>(4)
                runBlocking {
                    runCatching { supervisorScope { action() } }
                        .onSuccess { onSuccess(this, it) }
                        .onFailure { onError(it) }
                }
                mockk<Job>(relaxed = true)
            }
        }
        return Triple(stateFlow, CommonHandler(interactor, resourceWrapper, store), store)
    }

    /** The load-failure exit on both arms; both flags asserted, since either alone lies. */
    @Test
    fun `a load that throws clears isLoading and records the failure`() {
        coEvery { interactor.loadSession(any()) } throws IllegalStateException("db down")
        val (stateFlow, handler, store) = setup(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
        )
        // Precondition rather than assertion: a session route starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        assertTrue(stateFlow.value.loadFailed)
    }

    @Test
    fun `a session that is not there is abandoned the same way, not shown as empty`() {
        coEvery { interactor.loadSession(any()) } returns null
        val (stateFlow, handler, store) = setup(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
        )

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        assertTrue(stateFlow.value.loadFailed)
    }

    @Test
    fun `Init launches a load coroutine`() {
        val stateFlow = MutableStateFlow(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
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

        handler.invoke(Action.Common.Init)

        verify {
            store.launch(
                any(),
                any(),
                any(),
                any(),
                any<suspend CoroutineScope.() -> String?>(),
            )
        }
    }
}
