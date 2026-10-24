// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

internal class InputHandlerTest {

    private val initialState = State.create(uuid = null)
    private val stateFlow = MutableStateFlow(initialState)
    private val store = FakeInputHandlerStore(stateFlow)
    private val handler = InputHandler(store)

    @Test
    fun `OnNameChange updates name and clears nameError`() {
        stateFlow.value = stateFlow.value.copy(nameError = true)
        handler.invoke(Action.Input.OnNameChange("Bench"))
        assertEquals("Bench", stateFlow.value.name)
        assertEquals(false, stateFlow.value.nameError)
    }

    @Test
    fun `OnNameChange clears nameDuplicateError`() {
        stateFlow.value = stateFlow.value.copy(nameDuplicateError = true)
        handler.invoke(Action.Input.OnNameChange("New name"))
        assertEquals(false, stateFlow.value.nameDuplicateError)
    }

    @Test
    fun `OnDescriptionChange truncates to 2000 chars`() {
        val long = "a".repeat(3000)
        handler.invoke(Action.Input.OnDescriptionChange(long))
        assertEquals(2000, stateFlow.value.description.length)
    }

    @Test
    fun `OnTagSearchChange updates tagSearchQuery`() {
        handler.invoke(Action.Input.OnTagSearchChange("push"))
        assertEquals("push", stateFlow.value.tagSearchQuery)
    }

    /** [state] is the suite's flow and [updateState] applies to it; every other member fails fast. */
    private class FakeInputHandlerStore(
        private val flow: MutableStateFlow<State>,
    ) : ExerciseHandlerStore {

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by InputHandler")

        override val logger: Logger
            get() = error("logger must not be read by InputHandler")

        override fun sendEvent(event: Event): Nothing = error("sendEvent must not be used by InputHandler")

        override fun consume(action: Action): Nothing = error("consume must not be used by InputHandler")

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by InputHandler")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
            error("updateStateImmediate must not be used by InputHandler")

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by InputHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launch must not be used by InputHandler")

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launchDefault must not be used by InputHandler")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = error("Flow.launch must not be used by InputHandler")
    }
}
