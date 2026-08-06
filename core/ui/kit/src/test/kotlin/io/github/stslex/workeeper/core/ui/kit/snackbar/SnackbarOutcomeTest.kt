// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
internal class SnackbarOutcomeTest {

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
     */
    @Test
    fun `a show cancelled mid-flight requeues the model`() = runTest {
        val model = AppSnackbarModel(message = "requeue-show")
        var escaped = false
        try {
            resolveSnackbarOutcomeOrRequeue(model) {
                throw CancellationException("host recreating")
            }
        } catch (expected: CancellationException) {
            escaped = true
        }
        assertTrue(escaped)
        assertEquals("requeue-show", SnackbarManager.snackbar.first().message)
    }

    @Test
    fun `cancellation inside the commit requeues the model`() = runTest {
        val model = AppSnackbarModel(
            message = "requeue-commit",
            onDismissed = { throw CancellationException("host recreating mid-commit") },
        )
        var escaped = false
        try {
            resolveSnackbarOutcomeOrRequeue(model) { null }
        } catch (expected: CancellationException) {
            escaped = true
        }
        assertTrue(escaped)
        assertEquals("requeue-commit", SnackbarManager.snackbar.first().message)
    }

    @Test
    fun `a routed outcome does not requeue`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcomeOrRequeue(recorder.model()) { SnackbarResult.Dismissed }
        assertEquals(1, recorder.dismissals)
        // Nothing queued: an immediate poll of the singleton queue must come up empty.
        assertTrue(withTimeoutOrNull(POLL_MILLIS) { SnackbarManager.snackbar.first() } == null)
    }

    private companion object {
        const val POLL_MILLIS = 50L
    }
}
