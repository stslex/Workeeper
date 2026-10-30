// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins that `ChooseHandler` emits the choice and does NOT dismiss the dialog. The real observer
 * and repository run over [InMemoryPreferences], so both claims are effects (S3).
 */
internal class ChooseHandlerTest {

    private val repository = AppDialogRepository(InMemoryPreferences()) { EPOCH }
    private val observer = AppDialogObserverImpl(repository)

    @Test
    fun `Choose emits the choice to the observer`() = runTest {
        val received = mutableListOf<AppDialogUserChoice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeUserActions().toList(received)
        }
        val handler = ChooseHandler(observer, TestStore(this, StandardTestDispatcher(testScheduler)))
        val dialog = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)

        handler.invoke(Action.Choose(dialog = dialog, action = AppDialogUserAction.Report))
        advanceUntilIdle()

        assertEquals(listOf(AppDialogUserChoice(dialog, AppDialogUserAction.Report)), received)
    }

    @Test
    fun `Choose does NOT dismiss the dialog — the consumer is responsible`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 1_000L,
        )
        repository.publish(dialog)
        val handler = ChooseHandler(observer, TestStore(this, StandardTestDispatcher(testScheduler)))

        handler.invoke(Action.Choose(dialog = dialog, action = AppDialogUserAction.ConfirmUndo))
        advanceUntilIdle()

        assertEquals(dialog, repository.currentDialog.first())
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
    ) : AppDialogHandlerStore {

        override val state = MutableStateFlow(State.EMPTY)
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
        val TEST_UNDO_REF = UndoRef(
            RestoreOwnerId("00000000-0000-4000-8000-000000000011"),
        )
    }
}
