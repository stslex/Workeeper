// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ED11's window-close routing: «Отменить» never commits, a closed window always commits.
 * Each case asserts both lambdas, since the defect is delete-and-undo running together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SnackbarOutcomeTest {

    /**
     * GUARD: kermit's Logcat writer throws off-device, and the drain can reach a discard log.
     * Flip the call-time gate, not the captured logger. Same as [SnackbarManagerTest].
     */
    private var wasLogging = true

    @BeforeEach
    fun silenceLogSink() {
        wasLogging = Log.isLogging
        Log.isLogging = false
    }

    @AfterEach
    fun restoreLogSink() {
        Log.isLogging = wasLogging
    }

    /** [SnackbarManager]'s resolve gate is process-wide; a fenced leftover misroutes later. */
    @AfterEach
    fun reopenResolveGate() {
        SnackbarManager.unfenceResolves()
    }

    private class Recorder {
        var actions = 0
        var dismissals = 0

        fun model() = AppSnackbarModel(
            message = "m",
            actionLabel = "undo",
            action = { actions++ },
            onDismissed = { dismissals++ },
        )
    }

    @Test
    fun `ActionPerformed fires the action and never the commit`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcome(SnackbarResult.ActionPerformed, recorder.model())
        assertEquals(1, recorder.actions)
        assertEquals(0, recorder.dismissals)
    }

    @Test
    fun `Dismissed fires the commit and never the action`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcome(SnackbarResult.Dismissed, recorder.model())
        assertEquals(0, recorder.actions)
        assertEquals(1, recorder.dismissals)
    }

    @Test
    fun `a timed-out show — null — is a dismissal, and commits`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcome(null, recorder.model())
        assertEquals(0, recorder.actions)
        assertEquals(1, recorder.dismissals)
    }

    /** Both callbacks run in the app-level collector, so a throwing one must not cancel it. */
    @Test
    fun `a throwing commit is contained — the collector outlives it`() = runTest {
        val model = AppSnackbarModel(
            message = "m",
            onDismissed = { error("RESTRICT") },
        )
        resolveSnackbarOutcome(null, model)
    }

    @Test
    fun `a throwing action is contained — the collector outlives it`() = runTest {
        val model = AppSnackbarModel(
            message = "m",
            actionLabel = "undo",
            action = { error("boom") },
        )
        resolveSnackbarOutcome(SnackbarResult.ActionPerformed, model)
    }

    /** The collector's own stop signal is not a callback failure — it must still escape. */
    @Test
    fun `cancellation is not contained`() = runTest {
        val model = AppSnackbarModel(
            message = "m",
            onDismissed = { throw CancellationException("collector stopping") },
        )
        var escaped = false
        try {
            resolveSnackbarOutcome(null, model)
        } catch (expected: CancellationException) {
            escaped = true
        }
        assertTrue(escaped)
    }

    /** The requeue half; each case drains what it queues, the manager being a singleton. */
    @Test
    fun `a show cancelled mid-flight requeues the model`() = runTest {
        val delivered = deliverOnce(AppSnackbarModel(message = "requeue-show"))
        var escaped = false
        try {
            resolveSnackbarOutcomeOrRequeue(delivered) {
                throw CancellationException("host recreating")
            }
        } catch (expected: CancellationException) {
            escaped = true
        }
        assertTrue(escaped)
        assertEquals("requeue-show", SnackbarManager.snackbar.first().model.message)
    }

    @Test
    fun `cancellation inside the commit requeues the model`() = runTest {
        val delivered = deliverOnce(
            AppSnackbarModel(
                message = "requeue-commit",
                onDismissed = { throw CancellationException("host recreating mid-commit") },
            ),
        )
        var escaped = false
        try {
            resolveSnackbarOutcomeOrRequeue(delivered) { null }
        } catch (expected: CancellationException) {
            escaped = true
        }
        assertTrue(escaped)
        assertEquals("requeue-commit", SnackbarManager.snackbar.first().model.message)
    }

    @Test
    fun `a routed outcome does not requeue`() = runTest {
        val recorder = Recorder()
        val delivered = deliverOnce(recorder.model())
        resolveSnackbarOutcomeOrRequeue(delivered) { SnackbarResult.Dismissed }
        assertEquals(1, recorder.dismissals)
        // Nothing queued: an immediate poll of the singleton queue must come up empty.
        assertTrue(withTimeoutOrNull(POLL_MILLIS) { SnackbarManager.snackbar.first() } == null)
    }

    /**
     * The window has CLOSED once [AppSnackbarModel.onDismissed] is entered, so its commit runs
     * [NonCancellable]: a commit either never starts or finishes.
     */
    @Test
    fun `the host dying cannot tear a commit that began`() = runTest {
        var committed = false
        val model = AppSnackbarModel(
            message = "m",
            onDismissed = {
                delay(COMMIT_MILLIS)
                committed = true
            },
        )
        val job = launch { resolveSnackbarOutcome(null, model) }
        runCurrent() // the commit is mid-flight, suspended inside its own work
        job.cancel()
        advanceUntilIdle()
        assertTrue(committed)
    }

    /** Enqueues [model] through the real path so the [DeliveredSnackbar] carries the live epoch. */
    private suspend fun deliverOnce(model: AppSnackbarModel): DeliveredSnackbar {
        drainLeftovers()
        SnackbarManager.showSnackbar(model)
        val delivered = SnackbarManager.snackbar.first()
        assertEquals(model, delivered.model)
        return delivered
    }

    /** Drains the process-wide queue; the poll's null, not the approximate count, terminates. */
    private suspend fun drainLeftovers() {
        while (SnackbarManager.pendingModelCount > 0) {
            withTimeoutOrNull(POLL_MILLIS) { SnackbarManager.snackbar.first() } ?: return
        }
    }

    private companion object {
        const val POLL_MILLIS = 50L
        const val COMMIT_MILLIS = 100L
    }
}
