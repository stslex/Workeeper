// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.observer

import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins emit round-trip, `replay = 0` (no late-subscriber replay), and dismiss delegation. */
internal class AppDialogObserverImplTest {

    private val repository = mockk<AppDialogRepository>(relaxed = true)
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
    fun `late subscriber does NOT receive past emissions (replay = 0, no persisted-choice replay)`() =
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
                dialog = AppDialog.UndoRestoreSuccess,
                action = AppDialogUserAction.Acknowledge,
            )
            observer.emit(second)
            assertEquals(listOf(second), b.await())

            assertEquals(listOf(second), firstEmissionsObservedByLate)
        }

    @Test
    fun `acknowledgeReaction delegates to repository dismiss`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 100L)

        observer.acknowledgeReaction(dialog)

        coVerify(exactly = 1) { repository.dismiss(dialog) }
    }

    @Test
    fun `multiple subscribers all receive the same emission`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val a = async(dispatcher) { observer.observeUserActions().first() }
        val b = async(dispatcher) { observer.observeUserActions().first() }
        val choice = AppDialogUserChoice(
            dialog = AppDialog.UndoRestoreSuccess,
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
                    dialog = AppDialog.UndoRestoreSuccess,
                    action = AppDialogUserAction.Acknowledge,
                ),
            )
        }
        advanceUntilIdle()

        assert(emitJob.isCompleted) {
            "emit must not suspend when buffer capacity is available even with no subscribers"
        }
    }
}
