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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `isLoading`'s lifecycle on the training editor.
 *
 * Until the editors stage nothing read the flag — it was written on every load and consulted by no
 * composable, so an assertion about it was a claim about a field rather than about the app (§27:
 * "a gate whose subject is unconsumed is vacuous in precisely the way a green one is").
 * `SingleTrainingGraph` now withholds the whole screen while it is true (§26, "A route does not
 * compose until it has loaded").
 *
 * The consumer, named per §27's discriminator: that graph's
 * `if (state.isLoading) return@navComponentScreenWithState`, which decides whether
 * `TrainingDetailScreen` / `TrainingEditScreen` is composed at all. Not a test that reads the field.
 */
internal class CommonHandlerTest {

    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true).apply {
        every { observeAvailableTags() } returns flowOf(emptyList())
        every { observeAnyActiveSession() } returns emptyFlow()
    }
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    /**
     * The store mock runs `launch` synchronously and routes a throw to `onError`, because that
     * routing is the thing under test: production's default for `onError` is `{}` (B17, B21), so a
     * mock that swallowed the throw would report the defect as fixed. `supervisorScope` matches the
     * sibling fixture in `feature/exercise` — it is not needed for this handler's sequential load,
     * and keeping the two fixtures identical is worth more than shaving a wrapper off one of them.
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

    @Test
    fun `a create route never loads, so it is never withheld, and it opens clean`() {
        val (stateFlow, handler) = setup(State.create(uuid = null))

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        // The create branch also takes its dirty baseline here, so an untouched new training does
        // not open already dirty — which is what the discard sheet keys off.
        assertNotNull(stateFlow.value.originalSnapshot)
        assertFalse(stateFlow.value.hasChanges)
    }
}
