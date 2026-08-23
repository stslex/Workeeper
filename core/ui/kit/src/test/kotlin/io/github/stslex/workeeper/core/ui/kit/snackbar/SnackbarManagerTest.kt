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
     * The epoch tests below hit the discard branch, whose `logger.w {}` funnels into kermit's
     * Logcat writer — which throws on the JVM. The repo's `mockkObject(Log)` idiom cannot help
     * here: [SnackbarManager] is an object whose private logger was captured at class init
     * (possibly by an earlier test in this JVM), so stubbing `Log.tag` after the fact never
     * reaches it. [Log.isLogging] is the call-time gate in front of the kermit sink, and
     * `FirebaseCrashlyticsHolder` already self-guards into a no-op without an initialized
     * Firebase context — flipping the gate is the whole fix.
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
     * The resolve gate is process-wide state on the same singleton the queue lives on (R3), and
     * a fenced gate refuses EVERY later routing — so a fence test that failed midway would
     * poison every sibling in this JVM into silently requeueing instead of resolving. Reopening
     * admission after each case is the containment; [SnackbarManager.unfenceResolves] is
     * idempotent, so the cases that never fenced pay nothing.
     */
    @AfterEach
    fun reopenResolveGate() {
        SnackbarManager.unfenceResolves()
    }

    /**
     * Regression for the silent-drop bug: the real collector (`App.kt`) suspends inside
     * `SnackbarHostState.showSnackbar` for the whole time a snackbar is visible. A second
     * event emitted during that window must be buffered and delivered, not dropped. With a
     * zero-buffer `MutableSharedFlow` the second `tryEmit` returned `false` and the message
     * vanished — exactly the "no snackbar appears" symptom on the all-exercises blocked
     * bulk-archive path.
     */
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

    /**
     * The burst case the queue's KDoc forbids reintroducing: a deferred delete's model can
     * sit queued behind a visible toast while a burst of newer feedback arrives, and an
     * eviction there is a confirmed delete that silently never runs (its screen already
     * popped). Every entry must survive the burst, in order — a capped queue with any
     * overflow policy reds this.
     */
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

    /**
     * Phase 5 R2 (spec §8.4 Quiescing step 3): a COMMITTED generation handover must fence off
     * callbacks whose closures captured the replaced generation's repositories. A model queued
     * under epoch N is discarded at delivery once the epoch advanced — neither `action` nor
     * `onDismissed` may ever run — while a model enqueued AFTER the advance is the first (and
     * only) delivery, and routes normally.
     */
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

    /**
     * The other half of [SnackbarManager.advanceGenerationEpoch]'s contract: an ABORTED
     * transition never advances the epoch, so a queued model is preserved and delivers
     * normally when the outgoing generation resumes.
     */
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

    /**
     * Pins the stamp-at-enqueue contract that keeps the requeue path safe:
     * [SnackbarManager.showSnackbar] stamps the epoch CURRENT at enqueue, while
     * [SnackbarManager.requeue] copies the delivered model's OWN stamp back. Re-enqueueing the
     * same model instance through `showSnackbar` AFTER a committed handover is therefore a NEW
     * intent: the post-advance copy delivers (stamped N+1) while the pre-advance copy is
     * discarded — exactly one delivery.
     */
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

    /**
     * R3's requeue-epoch invariant, the DISCARD half. A model the host was still holding when
     * its generation was replaced goes back on the queue carrying its OWN epoch — the requeue
     * never re-stamps it as current — so a committed handover discards it at delivery exactly
     * like a model that never left the queue. Re-stamping (the bug [SnackbarManager.requeue]
     * replaces) would hand it to the SUCCESSOR's collector and run a callback closed over the
     * replaced generation's repositories; here neither callback may ever run.
     *
     * Both orderings are covered, and the SECOND is the one that actually separates «copies the
     * delivered stamp» from «reads the current epoch»: requeueing BEFORE the advance produces a
     * stale entry either way, so only a requeue that lands AFTER the advance — the fenced
     * refusal, which is exactly the case the fence makes reachable — can tell them apart.
     */
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

        // Ordering two: the handover committed while a collector sat between its delivery and
        // admission, so the refused routing requeues AFTER the epoch already moved. A requeue
        // that re-stamped would make this model look CURRENT and deliver it into the successor.
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

    /**
     * The PRESERVE half of the same invariant: an aborted transition never advances the epoch,
     * so the requeued model is still current and delivers again — with the very same stamp it
     * was enqueued under — to the collector of the generation that resumed. This is the case a
     * "drop on requeue" or a "re-stamp on requeue" implementation both get wrong, in opposite
     * directions, which is why the epoch is asserted and not just the delivery.
     */
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

    /**
     * Spec §8.4 step 3's admission half: once [SnackbarManager.fenceResolves] returned, the
     * transition owns the observation "no routing is running", and no collector may invalidate
     * it a moment later by starting one. So [SnackbarManager.beginResolve] refuses, and
     * [resolveSnackbarOutcomeOrRequeue] must not even SHOW the toast — it puts the model back
     * untouched. Reopening admission makes the very same delivery route normally, which is what
     * separates a fence from a drop.
     */
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

    /**
     * The quiesce half: [SnackbarManager.fenceResolves] observes zero in-flight routings AND
     * closes admission in one atomic step, so it must SUSPEND while a commit is running. The
     * commit here is the dangerous one — `onDismissed` inside `resolveSnackbarOutcome`'s
     * `NonCancellable` block, which the replacement cannot interrupt — so a fence that returned
     * early would let the runtime swap the generation out from under a half-applied delete.
     */
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
            // The commit really is mid-flight, parked on its gate — and the fence is waiting.
            assertFalse(committed)
            assertFalse(fenced)
        } finally {
            // Released even on a failed assertion: the commit is NonCancellable, so a gate left
            // closed would hang `runTest` on an uncancellable child instead of failing.
            commitGate.complete(Unit)
        }
        advanceUntilIdle()

        assertTrue(committed)
        assertTrue(fenced)
        resolveJob.join()
        fenceJob.join()
    }

    /**
     * Linearizability of the in-flight counter across overlapping hosts (a recreation overlaps
     * the outgoing collector with the incoming one, so two routings genuinely coexist). The
     * fence must wait for the LAST of them: releasing one commit leaves the count at one and
     * the fence still pending, and only the second release admits it. A counter that collapsed
     * to a boolean "is any resolve running" flag reds the middle assertion.
     */
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
        // Both gates are released even on a failed assertion: the commits are NonCancellable,
        // so a gate left closed would hang `runTest` on an uncancellable child.
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
     * [SnackbarManager] is a process-wide object: queue entries (and the generation epoch)
     * survive across tests in one JVM. Every epoch test starts by draining leftover ENTRIES
     * so its delivery assertions see only its own enqueues; stale-epoch entries are consumed
     * by the drain too, without emitting.
     *
     * Returns the post-drain [SnackbarManager.pendingModelCount] as the baseline for the
     * caller's final count assertion. The count is documented "approximate" and can sit
     * above zero with an EMPTY queue: `showSnackbar` increments after `trySend`, and a
     * collector parked on the channel under an unconfined dispatcher consumes the entry
     * inline INSIDE `trySend` — that decrement hits the `coerceAtLeast(0)` floor and is
     * swallowed, then the increment lands for the already-consumed entry (the sibling
     * buffering tests above create exactly this). So the epoch tests assert the count came
     * back TO THE BASELINE — every own enqueue was consumed — never absolute zero.
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

    /**
     * Enqueues [model] and takes its one delivery back off the flow, epoch stamp intact — the
     * only way a test can hold a [DeliveredSnackbar] carrying the CURRENT epoch, which is what
     * the requeue and fence cases below need (a hand-built stamp would silently be stale, since
     * sibling tests in this JVM advance the process-wide epoch).
     */
    private suspend fun deliverOnce(model: AppSnackbarModel): DeliveredSnackbar {
        SnackbarManager.showSnackbar(model)
        val delivered = SnackbarManager.snackbar.first()
        assertEquals(model, delivered.model)
        return delivered
    }

    /**
     * The host dies while the toast is on screen: `show` never returns an outcome, so `routed`
     * stays false and [resolveSnackbarOutcomeOrRequeue] puts the model back. The cancellation
     * is the collector's own stop signal and must still escape.
     */
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
