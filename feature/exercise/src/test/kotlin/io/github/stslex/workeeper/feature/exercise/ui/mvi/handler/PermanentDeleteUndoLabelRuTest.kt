// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.content.Context
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** The undo label of the deferred delete, resolved from the REAL Russian catalog — no mocks. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru")
internal class PermanentDeleteUndoLabelRuTest {

    @Test
    fun `the queued undo label resolves from the real russian catalog`() = runTest {
        while (SnackbarManager.pendingModelCount > 0) {
            SnackbarManager.snackbar.first()
        }
        val stateFlow = MutableStateFlow(
            State.create(uuid = "uuid-ru").copy(canPermanentlyDelete = true),
        )
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
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
        val handler = ClickHandler(
            interactor = mockk<ExerciseInteractor>(relaxed = true),
            resourceWrapper = mockk<ResourceWrapper>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            mainDispatcher = Dispatchers.Unconfined,
            store = store,
        )

        handler.invoke(Action.Click.OnConfirmPermanentDelete)

        assertEquals("Отменить", SnackbarManager.snackbar.first().model.actionLabel)
    }
}
