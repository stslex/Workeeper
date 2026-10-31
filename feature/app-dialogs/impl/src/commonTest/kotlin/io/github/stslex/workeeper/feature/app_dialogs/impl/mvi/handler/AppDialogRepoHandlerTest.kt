// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins routing for all three [Action.RepoAction] branches. Publish and Dismiss have no production
 * dispatcher today, so these tests are the only thing holding their wiring. The real repository
 * runs over [InMemoryPreferences], so each branch is pinned by its effect on the store (S3).
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

    private val repository = AppDialogRepository(InMemoryPreferences()) { EPOCH }

    @Test
    fun `Observe updates state when repository emits a dialog`() = runTest {
        val dialog = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_000L,
            previousVersionAvailable = true,
        )
        repository.publish(dialog)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Observe)
        runCurrent()

        assertEquals(dialog, store.state.value.current)
    }

    @Test
    fun `Observe sets state to null when repository emits null`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // A stale dialog in State, so a handler that dropped the null would leave it showing.
        val store = TestStore(
            this,
            dispatcher,
            initial = State(current = AppDialog.UndoRestoreSuccess(), dateLabel = "stale"),
        )
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Observe)
        runCurrent()

        assertNull(store.state.value.current)
    }

    @Test
    fun `Observe writes the dialog's formatted date beside it`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(
            undoRef = UndoRef(RestoreOwnerId("00000000-0000-4000-8000-000000000011")),
            originalDataDateEpochMs = 2_000L,
        )
        repository.publish(dialog)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Observe)
        runCurrent()

        assertEquals(State(current = dialog, dateLabel = "medium:2000"), store.state.value)
    }

    @Test
    fun `Publish forwards dialog to repository`() = runTest {
        val dialog = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Publish(dialog))
        advanceUntilIdle()

        assertEquals(dialog, repository.currentDialog.first())
    }

    @Test
    fun `Dismiss forwards dialog to repository`() = runTest {
        val dialog = AppDialog.UndoRestoreSuccess()
        repository.publish(dialog)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(this, dispatcher)
        val handler = AppDialogRepoHandler(repository, resources, store)

        handler.invoke(Action.RepoAction.Dismiss(dialog))
        advanceUntilIdle()

        assertNull(repository.currentDialog.first())
    }

    /**
     * S3: a store whose every edit completes on the caller's dispatcher, so `advanceUntilIdle()`
     * covers it; a file store completes on its own threads, which the test scheduler cannot see.
     */
    private class InMemoryPreferences : DataStore<Preferences> {

        private val current = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = current

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(current.value).also { current.value = it }
    }

    private class TestStore(
        private val testScope: TestScope,
        private val dispatcher: TestDispatcher,
        initial: State = State.EMPTY,
    ) : AppDialogHandlerStore {

        override val state = MutableStateFlow(initial)
        override val lastAction: Action? = null
        override val logger: Logger = SilentLogger

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

        // The repository flow never completes, so it is collected in backgroundScope, which the
        // test cancels at its end.
        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = this
            .catch { onError(it) }
            .onEach { withContext(eachDispatcher ?: dispatcher) { each(it) } }
            .flowOn(workDispatcher ?: dispatcher)
            .launchIn(testScope.backgroundScope)
    }

    /** The handlers log nothing; every level is a no-op, as the relaxed mock it replaces was. */
    private object SilentLogger : Logger {

        override fun e(throwable: Throwable, message: String?) = Unit

        override fun d(message: String) = Unit

        override fun d(e: Throwable, message: String) = Unit

        override fun d(e: Throwable, message: () -> String) = Unit

        override fun d(message: () -> String) = Unit

        override fun i(message: String) = Unit

        override fun i(message: () -> String) = Unit

        override fun v(message: String) = Unit

        override fun v(message: () -> String) = Unit

        override fun w(message: String) = Unit

        override fun w(message: () -> String) = Unit

        override fun w(message: String, throwable: Throwable) = Unit

        override fun w(throwable: Throwable, message: () -> String) = Unit
    }

    private companion object {
        val EPOCH = InstallEpoch(RestoreOwnerId("00000000-0000-4000-8000-000000000101"))
    }
}
