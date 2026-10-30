// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins routing for all three [Action.RepoAction] branches. Publish and Dismiss have no production
 * dispatcher today, so these tests are the only thing holding their wiring.
 */
internal class AppDialogRepoHandlerTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String =
            error("the handler reads no copy through ResourceWrapper; id $id")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("the handler reads no plural through ResourceWrapper; id $id")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("Not used in AppDialogRepoHandlerTest")

        override fun formatDayMonth(timestamp: Long): String =
            error("Not used in AppDialogRepoHandlerTest")

        override fun formatMediumDate(timestamp: Long): String = "medium:$timestamp"
    }

    @Test
    fun `Observe updates state when repository emits a dialog`() = runTest {
        val dialog = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_000L,
            previousVersionAvailable = true,
        )
        val repository = mockk<AppDialogRepository> {
            every { currentDialog } returns flowOf(dialog)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Observe)
        advanceUntilIdle()

        assertEquals(dialog, store.state.value.current)
    }

    @Test
    fun `Observe sets state to null when repository emits null`() = runTest {
        val repository = mockk<AppDialogRepository> {
            every { currentDialog } returns flowOf(null)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Observe)
        advanceUntilIdle()

        assertNull(store.state.value.current)
    }

    @Test
    fun `Observe writes the dialog's formatted date beside it`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(
            undoRef = UndoRef(RestoreOwnerId("00000000-0000-4000-8000-000000000011")),
            originalDataDateEpochMs = 2_000L,
        )
        val repository = mockk<AppDialogRepository> {
            every { currentDialog } returns flowOf(dialog)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Observe)
        advanceUntilIdle()

        assertEquals(State(current = dialog, dateLabel = "medium:2000"), store.state.value)
    }

    @Test
    fun `Publish forwards dialog to repository`() = runTest {
        val dialog = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        val repository = mockk<AppDialogRepository>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Publish(dialog))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.publish(dialog) }
    }

    @Test
    fun `Dismiss forwards dialog to repository`() = runTest {
        val dialog = AppDialog.UndoRestoreSuccess()
        val repository = mockk<AppDialogRepository>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Dismiss(dialog))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.dismiss(dialog) }
    }

    private class TestStore(
        private val testScope: TestScope,
        private val dispatcher: TestDispatcher,
    ) : AppDialogHandlerStore {

        override val state = MutableStateFlow(State.EMPTY)
        override val lastAction: Action? = null
        override val logger: Logger = mockk(relaxed = true)

        override fun sendEvent(event: Event) = Unit
        override fun consume(action: Action) = Unit
        override suspend fun consumeOnMain(action: Action) = Unit

        override fun updateState(update: (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(state: State) {
            this.state.value = state
        }

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = testScope.launch(workDispatcher ?: dispatcher) {
            runCatching { action() }
                .onSuccess { withContext(eachDispatcher ?: dispatcher) { onSuccess(it) } }
                .onFailure { withContext(eachDispatcher ?: dispatcher) { onError(it) } }
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = testScope.launch(dispatcher) {
            runCatching { action() }
                .onSuccess { withContext(dispatcher) { onSuccess(it) } }
                .onFailure { withContext(dispatcher) { onError(it) } }
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = this
            .catch { onError(it) }
            .onEach { withContext(eachDispatcher ?: dispatcher) { each(it) } }
            .flowOn(workDispatcher ?: dispatcher)
            .launchIn(testScope)
    }
}
