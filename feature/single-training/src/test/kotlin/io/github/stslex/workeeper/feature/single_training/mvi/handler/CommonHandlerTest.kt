// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `isLoading`'s lifecycle on the training editor. The consumer is `SingleTrainingGraph`'s
 * `if (state.isLoading) return@navComponentScreen`, which withholds the whole screen.
 */
internal class CommonHandlerTest {

    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true).apply {
        every { observeAvailableTags() } returns flowOf(emptyList())
        every { observeAnyActiveSession() } returns emptyFlow()
    }
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    /**
     * The store mock runs `launch` synchronously and routes a throw to `onError` — that routing is
     * the thing under test, since production's default `onError` is `{}`.
     */
    private fun setup(initialState: State): Pair<MutableStateFlow<State>, CommonHandler> {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<SingleTrainingHandlerStore>(relaxed = true).apply {
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
        return stateFlow to CommonHandler(interactor, resourceWrapper, store)
    }

    @Test
    fun `a load that throws clears isLoading, or the route is composed on nothing forever`() {
        coEvery { interactor.getTraining(any()) } throws IllegalStateException("db down")
        val (stateFlow, handler) = setup(State.create(uuid = "training-1"))
        // Precondition rather than assertion: a route with a uuid starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
    }

    @Test
    fun `a training that is not there clears isLoading too`() {
        coEvery { interactor.getTraining(any()) } returns null
        val (stateFlow, handler) = setup(State.create(uuid = "training-1"))

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
    }

    /** §3.3: the head's count is the total of finished sessions, not the visible page. */
    @Test
    fun `the История count is the total, not the visible page`() {
        coEvery { interactor.getTraining(any()) } returns mockk(relaxed = true)
        coEvery { interactor.getTrainingExercises(any()) } returns emptyList()
        coEvery { interactor.getRecentSessions(any(), any()) } returns emptyList()
        coEvery { interactor.countSessions(any()) } returns 12
        val (stateFlow, handler) = setup(State.create(uuid = "training-1"))

        handler.invoke(Action.Common.Init)

        assertEquals(12, stateFlow.value.historyCount)
    }

    @Test
    fun `a create route never loads, so it is never withheld, and it opens clean`() {
        val (stateFlow, handler) = setup(State.create(uuid = null))

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        // The create branch takes its dirty baseline here, so a new training opens clean.
        assertNotNull(stateFlow.value.originalSnapshot)
        assertFalse(stateFlow.value.hasChanges)
    }
}
