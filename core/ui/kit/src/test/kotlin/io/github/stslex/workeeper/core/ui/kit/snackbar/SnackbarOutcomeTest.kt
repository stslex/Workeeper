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
 * ED11's window-close signal at its selector. The consumer, named per §27's discriminator:
 * `App.kt`'s snackbar collector calls [resolveSnackbarOutcome] on every toast, and the
 * exercise feature's deferred permanent delete rides `onDismissed` — so «`Отменить` never
 * commits» and «a closed window always commits» are exactly the two branches here.
 *
 * Each case asserts BOTH lambdas — the fired one fired once and the other not at all —
 * because the defect this routing exists to prevent is delete-AND-undo running together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SnackbarOutcomeTest {

    /**
     * The drain below can meet a stale-epoch leftover from a sibling class in this JVM, and the
     * discard branch logs through kermit's Logcat writer, which throws off-device. Same fix and
     * same reason as [SnackbarManagerTest]: flip the call-time gate, not the captured logger.
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

    /**
     * [SnackbarManager]'s resolve gate is process-wide: a case that left it FENCED would make
     * every later [resolveSnackbarOutcomeOrRequeue] in this JVM requeue instead of route, so
     * admission is reopened after each case. Idempotent — the cases that never fence pay
     * nothing.
     */
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

    /**
     * The containment half of the routing's contract: both callbacks run inside the
     * app-level collector, which outlives every screen — a throwing commit (B-E7's RESTRICT
     * gap can reach one until its arc widens the eligibility predicate) must degrade to
     * B17/B21's silent class, not cancel the one collector every toast shares. These two
     * pass exactly when [resolveSnackbarOutcome] returns instead of rethrowing.
     */
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

    /**
     * The requeue half ([resolveSnackbarOutcomeOrRequeue]): the queue delivers once and the
     * collector dies with its composition, so a model the host holds when recreation
     * cancels it must go BACK — dropped, a deferred delete's confirmed commit silently
     * never runs while the process is alive. Each case drains what it queues: the manager
     * is a singleton and a leftover would leak into a sibling test.
     *
     * The model under test is taken off the real flow rather than hand-built: a
     * [DeliveredSnackbar] carries the generation epoch it was ENQUEUED under, and only the
     * live delivery path stamps the current one (sibling tests in this JVM advance it).
     */
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
     * The window has CLOSED once [AppSnackbarModel.onDismissed] is entered, so the commit
     * it carries runs [NonCancellable]: the host dying mid-transaction must not tear it in
     * half — and must not requeue a model whose delete already landed, which would re-show
     * an «Отменить» that can no longer undo anything. A commit either never starts (the
     * requeue's case) or finishes.
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

    /**
     * Enqueues [model] through the real path and takes its one delivery back, so the returned
     * [DeliveredSnackbar] carries the CURRENT generation epoch — the requeue cases above assert
     * on a redelivery, which a stale stamp would silently discard instead. Leftovers from a
     * sibling test in this JVM are drained first so the delivery taken back is this case's own.
     */
    private suspend fun deliverOnce(model: AppSnackbarModel): DeliveredSnackbar {
        drainLeftovers()
        SnackbarManager.showSnackbar(model)
        val delivered = SnackbarManager.snackbar.first()
        assertEquals(model, delivered.model)
        return delivered
    }

    /**
     * [SnackbarManager] is process-wide, so entries outlive the test that queued them. Consume
     * whatever is left until the queue stops producing — [SnackbarManager.pendingModelCount] is
     * documented approximate and can sit above zero over an empty queue, so the poll's null,
     * not the count, is the loop's real terminator.
     */
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
