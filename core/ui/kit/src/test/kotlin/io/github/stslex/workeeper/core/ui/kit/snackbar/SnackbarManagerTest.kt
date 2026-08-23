// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            SnackbarManager.snackbar.collect { model ->
                received += model.message
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
            SnackbarManager.snackbar.collect { model ->
                received += model.message
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
            SnackbarManager.snackbar.collect { model ->
                received += model.message
                // Mirror the App.kt collector's outcome routing for every DELIVERED model.
                resolveSnackbarOutcome(result = null, model = model)
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
                received += delivered
                resolveSnackbarOutcome(result = null, model = delivered)
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
     * [SnackbarManager.showSnackbar] stamps the epoch CURRENT at enqueue, and production
     * requeues ([resolveSnackbarOutcomeOrRequeue]) run during quiesce BEFORE the epoch
     * advances — a requeued model keeps its original generation's tag by ordering, not by
     * copying a stamp. Re-enqueueing the same model instance AFTER a committed handover is
     * therefore a NEW intent: the post-advance copy delivers (stamped N+1) while the
     * pre-advance copy is discarded — exactly one delivery.
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
            SnackbarManager.snackbar.collect { delivered -> received += delivered }
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(model), received)
        assertEquals(pendingBaseline, SnackbarManager.pendingModelCount)
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

    private companion object {
        const val SNACKBAR_VISIBLE_MILLIS = 1_000L

        /** One past the 16-slot buffer the queue must NOT have. */
        const val BURST_SIZE = 17
    }
}
