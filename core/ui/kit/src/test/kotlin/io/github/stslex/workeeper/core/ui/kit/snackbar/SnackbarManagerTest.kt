// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SnackbarManagerTest {

    /**
     * GUARD: silence the log sink for these tests — kermit's Logcat writer throws on the JVM, and
     * `mockkObject(Log)` cannot reach [SnackbarManager]'s logger, captured at class init.
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
     * GUARD: reopen the process-wide resolve gate after every case — a fence left closed poisons
     * every sibling in this JVM. [SnackbarManager.unfenceResolves] is idempotent.
     */
    @AfterEach
    fun reopenResolveGate() {
        SnackbarManager.unfenceResolves()
    }

    @Test
    fun `emissions while the collector is busy are buffered, not dropped`() = runTest {
        val received = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { delivered ->
                received += delivered.model.message
                // Mirror the App.kt collector suspending while the snackbar is shown.
                delay(SNACKBAR_VISIBLE_MILLIS)
            }
        }
        runCurrent() // let the collector subscribe before the first emission

        SnackbarManager.showSnackbar("first")
        // Emitted while the collector is still suspended showing "first".
        SnackbarManager.showSnackbar("second")

        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("first", "second"), received)
    }

    /** An eviction here is a confirmed delete that silently never runs — its screen has popped. */
    @Test
    fun `a commit queued behind a burst is delivered, never evicted`() = runTest {
        val received = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { delivered ->
                received += delivered.model.message
                delay(SNACKBAR_VISIBLE_MILLIS)
            }
        }
        runCurrent()

        SnackbarManager.showSnackbar("visible")
        SnackbarManager.showSnackbar("deferred-commit")
        repeat(BURST_SIZE) { index -> SnackbarManager.showSnackbar("burst-$index") }

        advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf("visible", "deferred-commit") + List(BURST_SIZE) { "burst-$it" },
            received,
        )
    }

    /** Spec §8.4: a committed handover discards models stamped with the outgoing epoch. */
    @Test
    fun `a model queued before a committed handover is DISCARDED at delivery - its callbacks never run`() = runTest {
        val pendingBaseline = drainLeftovers()
        var staleActionRan = false
        var staleDismissRan = false
        var freshDismissRan = false
        val stale = AppSnackbarModel(
            message = "stale-before-handover",
            actionLabel = "undo",
            action = { staleActionRan = true },
            onDismissed = { staleDismissRan = true },
        )
        SnackbarManager.showSnackbar(stale)
        SnackbarManager.advanceGenerationEpoch()
        SnackbarManager.showSnackbar(
            AppSnackbarModel(
                message = "fresh-after-handover",
                onDismissed = { freshDismissRan = true },
            ),
        )

        val received = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { delivered ->
                received += delivered.model.message
                // Mirror the App.kt collector's outcome routing for every DELIVERED model.
                resolveSnackbarOutcome(result = null, model = delivered.model)
            }
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("fresh-after-handover"), received)
        assertTrue(freshDismissRan)
        assertFalse(staleActionRan)
        assertFalse(staleDismissRan)
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
    }

    @Test
    fun `an aborted transition preserves the queue - no advance, the model delivers normally`() = runTest {
        val pendingBaseline = drainLeftovers()
        var dismissRan = false
        val model = AppSnackbarModel(
            message = "aborted-transition-survivor",
            onDismissed = { dismissRan = true },
        )
        SnackbarManager.showSnackbar(model)

        val received = mutableListOf<AppSnackbarModel>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { delivered ->
                received += delivered.model
                resolveSnackbarOutcome(result = null, model = delivered.model)
            }
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(model), received)
        assertTrue(dismissRan)
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
    }

    /** `showSnackbar` stamps the epoch at enqueue; `requeue` copies the delivered model's own. */
    @Test
    fun `re-enqueue after the advance delivers exactly once - the pre-advance copy is discarded`() = runTest {
        val pendingBaseline = drainLeftovers()
        val model = AppSnackbarModel(message = "re-enqueued-intent")

        SnackbarManager.showSnackbar(model) // stamped with epoch N
        SnackbarManager.advanceGenerationEpoch() // committed handover: N -> N+1
        SnackbarManager.showSnackbar(model) // a NEW intent, stamped with epoch N+1

        val received = mutableListOf<AppSnackbarModel>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { delivered -> received += delivered.model }
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(model), received)
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
    }

    /** Both orderings covered; only a requeue landing after the advance separates the two. */
    @Test
    fun `a requeued model keeps its ORIGINAL epoch and is discarded after a committed handover`() = runTest {
        val pendingBaseline = drainLeftovers()
        var actionRan = false
        var dismissRan = false
        val delivered = deliverOnce(
            AppSnackbarModel(
                message = "requeued-across-handover",
                actionLabel = "undo",
                action = { actionRan = true },
                onDismissed = { dismissRan = true },
            ),
        )
        requeueOnHostDeath(delivered)

        // The handover COMMITTED after the host died holding the model.
        SnackbarManager.advanceGenerationEpoch()

        assertNull(nextDeliveryOrNull())
        assertFalse(actionRan)
        assertFalse(dismissRan)

        // Ordering two: the handover committed while a collector sat between delivery and
        // admission, so the refused routing requeues AFTER the epoch moved.
        var lateActionRan = false
        var lateDismissRan = false
        var lateShows = 0
        val late = deliverOnce(
            AppSnackbarModel(
                message = "requeued-behind-the-fence",
                actionLabel = "undo",
                action = { lateActionRan = true },
                onDismissed = { lateDismissRan = true },
            ),
        )
        SnackbarManager.fenceResolves()
        SnackbarManager.advanceGenerationEpoch()
        resolveSnackbarOutcomeOrRequeue(late) {
            lateShows++
            SnackbarResult.Dismissed
        }
        SnackbarManager.unfenceResolves()

        assertEquals(0, lateShows)
        assertNull(nextDeliveryOrNull())
        assertFalse(lateActionRan)
        assertFalse(lateDismissRan)
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
    }

    @Test
    fun `an aborted transition preserves a requeued model - it delivers when the generation resumes`() = runTest {
        val pendingBaseline = drainLeftovers()
        var dismissRan = false
        val model = AppSnackbarModel(
            message = "requeued-abort-survivor",
            onDismissed = { dismissRan = true },
        )
        val delivered = deliverOnce(model)
        requeueOnHostDeath(delivered)

        // No advance: the transition ABORTED and the outgoing generation resumed.
        val redelivered = requireNotNull(nextDeliveryOrNull())

        assertEquals(model, redelivered.model)
        assertEquals(delivered.epoch, redelivered.epoch)
        resolveSnackbarOutcome(result = null, model = redelivered.model)
        assertTrue(dismissRan)
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
    }

    /** A fenced routing is not shown at all: the model goes back on the queue untouched. */
    @Test
    fun `no new resolve can start after the fence`() = runTest {
        val pendingBaseline = drainLeftovers()
        var shows = 0
        var dismissals = 0
        val model = AppSnackbarModel(
            message = "fenced-resolve",
            onDismissed = { dismissals++ },
        )
        val delivered = deliverOnce(model)

        SnackbarManager.fenceResolves()
        assertFalse(SnackbarManager.beginResolve())

        resolveSnackbarOutcomeOrRequeue(delivered) {
            shows++
            SnackbarResult.Dismissed
        }

        assertEquals(0, shows)
        assertEquals(0, dismissals)
        val requeued = requireNotNull(nextDeliveryOrNull())
        assertEquals(model, requeued.model)
        assertEquals(delivered.epoch, requeued.epoch)

        SnackbarManager.unfenceResolves()
        resolveSnackbarOutcomeOrRequeue(requeued) {
            shows++
            SnackbarResult.Dismissed
        }

        assertEquals(1, shows)
        assertEquals(1, dismissals)
        assertNull(nextDeliveryOrNull())
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
    }

    @Test
    fun `fenceResolves waits for an in-flight NonCancellable onDismissed`() = runTest {
        drainLeftovers()
        val commitGate = CompletableDeferred<Unit>()
        var committed = false
        val delivered = deliverOnce(
            AppSnackbarModel(
                message = "in-flight-commit",
                onDismissed = {
                    commitGate.await()
                    committed = true
                },
            ),
        )

        val resolveJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolveSnackbarOutcomeOrRequeue(delivered) { SnackbarResult.Dismissed }
        }
        runCurrent()

        var fenced = false
        val fenceJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.fenceResolves()
            fenced = true
        }
        runCurrent()
        try {
            assertFalse(committed)
            assertFalse(fenced)
        } finally {
            // GUARD: release in `finally` — the commit is NonCancellable, so a gate left closed
            // hangs `runTest` instead of reporting the failure.
            commitGate.complete(Unit)
        }
        advanceUntilIdle()

        assertTrue(committed)
        assertTrue(fenced)
        resolveJob.join()
        fenceJob.join()
    }

    /** A boolean "is any resolve running" flag instead of a counter reds the middle assertion. */
    @Test
    fun `two overlapping collectors keep the in-flight accounting linearizable`() = runTest {
        drainLeftovers()
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val firstDelivered = deliverOnce(
            AppSnackbarModel(message = "overlap-first", onDismissed = { firstGate.await() }),
        )
        val secondDelivered = deliverOnce(
            AppSnackbarModel(message = "overlap-second", onDismissed = { secondGate.await() }),
        )

        val firstJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolveSnackbarOutcomeOrRequeue(firstDelivered) { SnackbarResult.Dismissed }
        }
        val secondJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolveSnackbarOutcomeOrRequeue(secondDelivered) { SnackbarResult.Dismissed }
        }
        runCurrent()

        var fenced = false
        val fenceJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.fenceResolves()
            fenced = true
        }
        runCurrent()
        // GUARD: release both gates in `finally` — the commits are NonCancellable, so a gate left
        // closed hangs `runTest`.
        try {
            assertFalse(fenced)

            firstGate.complete(Unit)
            advanceUntilIdle()
            assertTrue(firstJob.isCompleted)
            assertFalse(fenced) // one routing still in flight — the fence may not admit yet
        } finally {
            firstGate.complete(Unit)
            secondGate.complete(Unit)
        }
        advanceUntilIdle()

        assertTrue(fenced)
        firstJob.join()
        secondJob.join()
        fenceJob.join()
    }

    /**
     * Drains the process-wide queue's leftovers and returns [SnackbarManager.pendingModelCount] as
     * a baseline — the count is approximate, so assert against it rather than against zero.
     */
    private fun TestScope.drainLeftovers(): Int {
        if (SnackbarManager.pendingModelCount > 0) {
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                SnackbarManager.snackbar.collect { }
            }
            advanceUntilIdle()
            job.cancel()
        }
        return SnackbarManager.pendingModelCount
    }

    /** Enqueues [model] and takes its one delivery back off the flow, epoch stamp intact. */
    private suspend fun deliverOnce(model: AppSnackbarModel): DeliveredSnackbar {
        SnackbarManager.showSnackbar(model)
        val delivered = SnackbarManager.snackbar.first()
        assertEquals(model, delivered.model)
        return delivered
    }

    /** Host dies mid-toast: `show` never returns, so the model is requeued and cancel escapes. */
    private suspend fun requeueOnHostDeath(delivered: DeliveredSnackbar) {
        var escaped = false
        try {
            resolveSnackbarOutcomeOrRequeue(delivered) {
                throw CancellationException("host recreating")
            }
        } catch (expected: CancellationException) {
            escaped = true
        }
        assertTrue(escaped)
    }

    /** The next delivery, or null when the queue produces none within [POLL_MILLIS]. */
    private suspend fun nextDeliveryOrNull(): DeliveredSnackbar? = withTimeoutOrNull(POLL_MILLIS) {
        SnackbarManager.snackbar.first()
    }

    private companion object {
        const val SNACKBAR_VISIBLE_MILLIS = 1_000L

        /** One past the 16-slot buffer the queue must NOT have. */
        const val BURST_SIZE = 17

        /** Virtual-time budget for "nothing more is delivered" — `runTest` skips it instantly. */
        const val POLL_MILLIS = 50L
    }
}
