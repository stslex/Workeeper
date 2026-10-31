// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.observer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins emit round-trip, `replay = 0` (no late-subscriber replay), and dismiss delegation. The real
 * repository runs over [InMemoryPreferences], so the delegation is pinned by its effect (S3).
 */
internal class AppDialogObserverImplTest {

    private val repository = AppDialogRepository(InMemoryPreferences()) { EPOCH }
    private val observer = AppDialogObserverImpl(repository)

    @Test
    fun `emit delivers the choice to a live subscriber`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val received = async(dispatcher) { observer.observeUserActions().first() }
        // Subscriber has joined (UnconfinedTestDispatcher starts collecting eagerly).
        val choice = AppDialogUserChoice(
            dialog = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown),
            action = AppDialogUserAction.Report,
        )

        observer.emit(choice)

        assertEquals(choice, received.await())
    }

    @Test
    fun `late subscriber does NOT receive past emissions — replay = 0 and no persisted-choice replay`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)

            val firstEmissionsObservedByLate = mutableListOf<AppDialogUserChoice>()

            // Subscriber A — receives the first emission, then disposes.
            val a = async(dispatcher) { observer.observeUserActions().take(1).toList() }
            val first = AppDialogUserChoice(
                dialog = AppDialog.RestoreSuccess(0L, false),
                action = AppDialogUserAction.Acknowledge,
            )
            observer.emit(first)
            assertEquals(listOf(first), a.await())

            // Subscriber B joins after `first`; with replay = 0 it must not see it.
            val b = async(dispatcher) {
                observer.observeUserActions().take(1).toList().also {
                    firstEmissionsObservedByLate.addAll(it)
                }
            }
            val second = AppDialogUserChoice(
                dialog = AppDialog.UndoRestoreSuccess(),
                action = AppDialogUserAction.Acknowledge,
            )
            observer.emit(second)
            assertEquals(listOf(second), b.await())

            assertEquals(listOf(second), firstEmissionsObservedByLate)
        }

    @Test
    fun `acknowledgeReaction delegates to repository dismiss`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 100L,
        )

        repository.publish(dialog)

        observer.acknowledgeReaction(dialog)

        assertNull(repository.currentDialog.first())
    }

    @Test
    fun `multiple subscribers all receive the same emission`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val a = async(dispatcher) { observer.observeUserActions().first() }
        val b = async(dispatcher) { observer.observeUserActions().first() }
        val choice = AppDialogUserChoice(
            dialog = AppDialog.UndoRestoreSuccess(),
            action = AppDialogUserAction.Acknowledge,
        )

        observer.emit(choice)

        assertEquals(choice, a.await())
        assertEquals(choice, b.await())
    }

    @Test
    fun `emit with no subscriber buffers up to capacity without suspending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        // With extraBufferCapacity = 64 an emit with no subscriber buffers instead of suspending.
        val emitJob = launch(dispatcher) {
            observer.emit(
                AppDialogUserChoice(
                    dialog = AppDialog.UndoRestoreSuccess(),
                    action = AppDialogUserAction.Acknowledge,
                ),
            )
        }
        advanceUntilIdle()

        assertTrue(
            emitJob.isCompleted,
            "emit must not suspend when buffer capacity is available even with no subscribers",
        )
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

    private companion object {
        val EPOCH = InstallEpoch(RestoreOwnerId("00000000-0000-4000-8000-000000000101"))
        val TEST_UNDO_REF = UndoRef(
            RestoreOwnerId("00000000-0000-4000-8000-000000000011"),
        )
    }
}
