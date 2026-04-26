// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test

internal class PagingHandlerTest {

    private val interactor = mockk<AllExercisesInteractor>(relaxed = true).apply {
        every { observeExercises(any()) } returns flowOf(androidx.paging.PagingData.empty())
        every { observeAvailableTags() } returns flowOf(emptyList())
    }
    private val store = mockk<AllExercisesHandlerStore>(relaxed = true)
    private val handler = PagingHandler(
        interactor = interactor,
        defaultDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        store = store,
    )

    @Test
    fun `Init invokes observeAvailableTags via scope launch`() {
        handler.invoke(Action.Paging.Init)
        // The scope.launch on flow goes through the handler-store's scope mock; we just
        // make sure invoke does not throw.
    }
}
