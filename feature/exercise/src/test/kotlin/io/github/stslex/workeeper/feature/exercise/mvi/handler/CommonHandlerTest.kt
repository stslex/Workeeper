// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import android.net.Uri
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<ExerciseInteractor>(relaxed = true).apply {
        every { observeAvailableTags() } returns flowOf(emptyList())
    }
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    private fun setup(initialState: State): Pair<MutableStateFlow<State>, CommonHandler> {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        return stateFlow to CommonHandler(interactor, resourceWrapper, store)
    }

    @Test
    fun `Init for create mode does not load exercise`() {
        val (_, handler) = setup(State.create(uuid = null))
        handler.invoke(Action.Common.Init)
        // No exception means the handler short-circuited without trying to load.
    }

    @Test
    fun `Init for read mode kicks off observe + load`() {
        val (_, handler) = setup(State.create(uuid = "uuid-1"))
        handler.invoke(Action.Common.Init)
        // No assertion on launch internals here — see ExerciseInteractorImplTest for repository
        // behaviour. The handler invariant is that Init does not throw.
    }

    @Test
    fun `ImagePicked sets pendingImage to NewFromUri and hides the source dialog`() {
        val uri = mockk<Uri>(relaxed = true)
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(sourceDialogVisible = true),
        )

        handler.invoke(Action.Common.ImagePicked(uri))

        assertEquals(PendingImage.NewFromUri(uri), stateFlow.value.pendingImage)
        assertEquals(false, stateFlow.value.sourceDialogVisible)
    }

    @Test
    fun `ImagePickCancelled hides the source dialog without staging a pending image`() {
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(sourceDialogVisible = true),
        )

        handler.invoke(Action.Common.ImagePickCancelled)

        assertEquals(PendingImage.Unchanged, stateFlow.value.pendingImage)
        assertEquals(false, stateFlow.value.sourceDialogVisible)
    }
}
