// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InputHandlerTest {

    private val initialState = State.create(uuid = null)
    private val stateFlow = MutableStateFlow(initialState)
    private val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
    }
    private val handler = InputHandler(store)

    @Test
    fun `OnNameChange updates name and clears nameError`() {
        stateFlow.value = stateFlow.value.copy(nameError = true)
        handler.invoke(Action.Input.OnNameChange("Bench"))
        assertEquals("Bench", stateFlow.value.name)
        assertEquals(false, stateFlow.value.nameError)
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
}
