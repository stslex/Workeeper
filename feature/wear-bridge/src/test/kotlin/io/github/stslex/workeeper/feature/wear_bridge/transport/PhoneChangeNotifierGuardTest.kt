// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import androidx.sqlite.SQLiteException
import io.github.stslex.workeeper.feature.wear_bridge.WatchKnownRevisions
import io.github.stslex.workeeper.feature.wear_bridge.WatchStateKey
import io.github.stslex.workeeper.feature.wear_bridge.transport.FakeNudgeLink.Companion.WATCH_A
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * wear-live-sync.md §6.2 and §10.1, D13: the notifier's one guard around its whole collection. A key
 * query that fails ends the notifier wherever the failure arrives, with exactly one `signal stopped`
 * line, nothing signalled afterwards and nothing reaching the scope's exception handler (D11); only
 * the notifier's own cancellation propagates. Every time here derives from the notifier's constants,
 * so a change of their values cannot move a case to another stage (F32).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PhoneChangeNotifierGuardTest {

    private val known = WatchKnownRevisions()
    private val link = FakeNudgeLink()
    private val logger = SignalLogger()
    private val escaped: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())

    /** The generation lifetime the notifier runs on (§6.3). */
    private val lifetime = SupervisorJob()
    private val changed = WatchStateKey.of(Uuid.random(), revision = 2)
    private val failure = SQLiteException("connection pool is closed")

    @AfterEach
    fun tearDown() {
        lifetime.cancel()
    }

    // region a failing key query

    @Test
    fun `a key query that fails before its first value ends the notifier`() = runTest {
        val notifier = launchNotifier(flow { throw failure })
        advanceUntilIdle()

        assertStoppedBy(failure, notifier)
    }

    @Test
    fun `a key query that fails while a change settles ends the notifier before the signal`() = runTest {
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                delay(CHANGE_SETTLE_MS / 2)
                throw failure
            },
        )
        advanceUntilIdle()

        assertStoppedBy(failure, notifier)
    }

    @Test
    fun `a key query that fails while a lookup is suspended ends the round and the notifier`() = runTest {
        val lookup = CompletableDeferred<Unit>()
        link.lookupGate = lookup
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                delay(CHANGE_SETTLE_MS * 2)
                throw failure
            },
        )
        advanceTimeBy(CHANGE_SETTLE_MS)
        runCurrent()
        assertEquals(listOf("lookup"), link.calls, "the round started and its lookup is suspended")

        advanceUntilIdle()
        lookup.complete(Unit)
        advanceUntilIdle()

        assertStoppedBy(failure, notifier)
    }

    @Test
    fun `a key query that fails while a send is suspended ends the round and the notifier`() = runTest {
        val send = CompletableDeferred<Unit>()
        link.sendGate = send
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                delay(CHANGE_SETTLE_MS * 2)
                throw failure
            },
        )
        advanceTimeBy(CHANGE_SETTLE_MS)
        runCurrent()
        assertEquals(listOf("lookup", "send"), link.calls, "the round's send is suspended")

        advanceUntilIdle()
        send.complete(Unit)
        advanceUntilIdle()

        assertStoppedBy(failure, notifier)
    }

    @Test
    fun `a key query that fails inside the minimum interval ends the notifier after its one signal`() = runTest {
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                delay(CHANGE_SETTLE_MS + CHANGE_MIN_INTERVAL_MS / 2)
                throw failure
            },
        )
        advanceUntilIdle()

        assertStoppedBy(failure, notifier, signalled = listOf(WATCH_A))
    }

    // endregion

    // region a cancellation that is not the notifier's own

    @Test
    fun `a foreign cancellation from the key query before its first value is logged and ends it`() = runTest {
        val foreign = CancellationException("not the notifier's")
        val notifier = launchNotifier(flow { throw foreign })
        advanceUntilIdle()

        assertStoppedBy(foreign, notifier)
    }

    @Test
    fun `a foreign cancellation from the key query while a change settles is logged and ends it`() = runTest {
        val foreign = CancellationException("not the notifier's")
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                delay(CHANGE_SETTLE_MS / 2)
                throw foreign
            },
        )
        advanceUntilIdle()

        assertStoppedBy(foreign, notifier)
    }

    // endregion

    // region the notifier's own cancellation

    @Test
    fun `cancelling the lifetime inside the minimum interval logs no stop`() = runTest {
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                awaitCancellation()
            },
        )
        advanceTimeBy(CHANGE_SETTLE_MS + CHANGE_MIN_INTERVAL_MS / 2)
        runCurrent()
        assertEquals(listOf(WATCH_A), link.signals, "one round ran and its interval is running")

        lifetime.cancel()
        advanceUntilIdle()

        assertCancelledWithoutStop(notifier)
    }

    @Test
    fun `cancelling the lifetime while a lookup is suspended logs no stop`() = runTest {
        link.lookupGate = CompletableDeferred()
        val notifier = launchNotifier(
            flow {
                emit(null)
                emit(changed)
                awaitCancellation()
            },
        )
        advanceTimeBy(CHANGE_SETTLE_MS)
        runCurrent()
        assertEquals(listOf("lookup"), link.calls, "the round's lookup is suspended")

        lifetime.cancel()
        advanceUntilIdle()

        assertCancelledWithoutStop(notifier)
    }

    // endregion

    private fun TestScope.launchNotifier(keys: Flow<WatchStateKey?>): Job {
        val handler = CoroutineExceptionHandler { _, thrown -> escaped += thrown }
        return CoroutineScope(StandardTestDispatcher(testScheduler) + lifetime + handler).launch {
            signalChanges(keys, known, link, logger)
        }
    }

    private fun assertStoppedBy(cause: Throwable, notifier: Job, signalled: List<String> = emptyList()) {
        assertTrue(notifier.isCompleted && !notifier.isCancelled, "the notifier ended normally")
        assertEquals(emptyList<Throwable>(), escaped, "nothing reached the scope's exception handler (D11)")
        assertEquals(
            listOf("signal stopped: ${cause::class.simpleName}"),
            logger.lines.filter { it.startsWith("signal stopped") },
            "exactly one stop line, by class",
        )
        assertEquals(signalled, link.signals, "nothing signalled after the failure")
    }

    private fun assertCancelledWithoutStop(notifier: Job) {
        assertTrue(notifier.isCancelled, "the notifier's own cancellation propagated")
        assertEquals(emptyList<Throwable>(), escaped, "nothing reached the scope's exception handler")
        assertEquals(emptyList<String>(), logger.lines.filter { it.startsWith("signal stopped") })
    }
}
