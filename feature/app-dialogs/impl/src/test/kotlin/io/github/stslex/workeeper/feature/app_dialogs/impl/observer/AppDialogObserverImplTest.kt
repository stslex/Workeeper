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

/**
 * `AppDialogObserverImpl` is the transient-signal transport for user
 * choices. Tests pin:
 *
 *  - emit → subscriber round-trip on a live subscriber.
 *  - hot SharedFlow semantics: a late subscriber does NOT replay past
 *    emissions (replay = 0).
 *  - `acknowledgeReaction(dialog)` delegates to `repository.dismiss(dialog)`.
 *
 * A late subscriber NOT receiving past emissions is the LOAD-BEARING
 * property — it's what eliminates the persisted-choice replay risk
 * (BLOCKER 2). The corresponding bootstrap requirement is that consumers
 * must register their subscriber at `BaseApplication.onCreate` before any
 * UI dispatch — see [io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver]
 * KDoc.
 */
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

            // No subscribers yet — emit must NOT be retained for future subscribers.
            // We can't directly call observer.emit() with no subscribers (it would
            // suspend on SUSPEND-overflow with no buffer-space proof), so instead
            // verify replay semantics by checking that a fresh subscriber only sees
            // emissions that happen AFTER it subscribed.

            val firstEmissionsObservedByLate = mutableListOf<AppDialogUserChoice>()

            // Subscriber A — receives the first emission, then disposes.
            val a = async(dispatcher) { observer.observeUserActions().take(1).toList() }
            val first = AppDialogUserChoice(
                dialog = AppDialog.RestoreSuccess(0L, false),
                action = AppDialogUserAction.Acknowledge,
            )
            observer.emit(first)
            assertEquals(listOf(first), a.await())

            // Subscriber B — subscribes AFTER `first` was already emitted. With
            // replay = 0, B must NOT see `first`. B only sees emissions that
            // happen during its active subscription.
            val b = async(dispatcher) {
                observer.observeUserActions().take(1).toList().also {
                    firstEmissionsObservedByLate.addAll(it)
                }
            }
            // Late subscriber is active now; emit a second event.
            val second = AppDialogUserChoice(
                dialog = AppDialog.UndoRestoreSuccess,
                action = AppDialogUserAction.Acknowledge,
            )
            observer.emit(second)
            assertEquals(listOf(second), b.await())

            // The crucial assertion: B saw `second`, NOT `first`.
            assertEquals(listOf(second), firstEmissionsObservedByLate)
        }

    @Test
    fun `acknowledgeReaction delegates to repository dismiss`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 100L)

        observer.acknowledgeReaction(dialog)

        // `repository` is relaxed — dismiss returns Unit cleanly; we just
        // assert the delegation happened.
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

        // No subscriber. Emit one event. With extraBufferCapacity = 64 and
        // BufferOverflow.SUSPEND, a single emit lands in the buffer and
        // completes without suspending. We can't directly assert non-suspension
        // without time/timeout instrumentation, but we can assert the emit
        // returns (the test won't hang). The subsequent first-subscriber
        // attached after the emit will NOT see it (replay = 0) — that
        // property is already covered by the "late subscriber" test above.

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
