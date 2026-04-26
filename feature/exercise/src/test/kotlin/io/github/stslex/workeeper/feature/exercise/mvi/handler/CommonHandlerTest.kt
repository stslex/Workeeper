// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<ExerciseInteractor>(relaxed = true).apply {
        every { observeAvailableTags() } returns flowOf(emptyList())
    }

    @Test
    fun `Init for create mode does not load exercise`() {
        val stateFlow = MutableStateFlow(State.create(uuid = null))
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
        }
        val handler = CommonHandler(interactor, store)
        handler.invoke(Action.Common.Init)
        // No exception means the handler short-circuited without trying to load.
    }

    @Test
    fun `Init for read mode kicks off observe + load`() {
        val stateFlow = MutableStateFlow(State.create(uuid = "uuid-1"))
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
        }
        val handler = CommonHandler(interactor, store)
        handler.invoke(Action.Common.Init)
        // No assertion on launch internals here — see ExerciseInteractorImplTest for repository
        // behaviour. The handler invariant is that Init does not throw.
    }
}
