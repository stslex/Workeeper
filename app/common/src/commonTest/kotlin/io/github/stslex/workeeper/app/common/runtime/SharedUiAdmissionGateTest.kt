// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.common.runtime

import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedUiAdmissionGateTest {
    @Test
    fun foreignAndRepeatedReleaseCannotSpendAnotherGrant() = runBlocking {
        val a = UiAdmissionGate(Log.tag("SharedUiAdmissionGateTest-A"))
        val b = UiAdmissionGate(Log.tag("SharedUiAdmissionGateTest-B"))
        val first = assertNotNull(a.admit(1))
        val foreign = assertNotNull(b.admit(1))
        assertEquals(1, a.admittedCount(1))
        assertEquals(1, b.admittedCount(1))

        b.release(first)
        a.release(foreign)
        a.release(object : AppUiAdmissionToken {})
        assertEquals(1, a.admittedCount(1), "B's grant cannot spend A's grant")
        assertEquals(1, b.admittedCount(1), "A's grant cannot spend B's grant")

        val second = assertNotNull(a.admit(1))
        assertEquals(2, a.admittedCount(1))
        a.release(first)
        a.release(first)
        assertEquals(1, a.admittedCount(1))
        assertFalse(a.awaitRetired(1, timeoutMillis = 50))
        a.release(second)
        b.release(foreign)
        assertEquals(0, a.admittedCount(1))
        assertEquals(0, b.admittedCount(1))
    }

    @Test
    fun retireWaitsForTheLastGrantAndReopenIsAbaSafe() = runBlocking {
        val gate = UiAdmissionGate(Log.tag("SharedUiAdmissionGateTest"))
        val first = assertNotNull(gate.admit(1))
        val last = assertNotNull(gate.admit(1))
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitRetired(1, timeoutMillis = 10_000)
        }
        assertFalse(waiter.isCompleted)
        waiter.cancel()
        assertFailsWith<CancellationException> { waiter.await() }
        waiter.join()
        assertEquals(2, gate.admittedCount(1))
        val afterCancel = assertNotNull(gate.admit(1))
        gate.release(afterCancel)

        assertFalse(gate.awaitRetired(1, timeoutMillis = 50))
        val afterTimeout = assertNotNull(gate.admit(1))
        gate.release(afterTimeout)
        gate.release(first)
        assertEquals(1, gate.admittedCount(1))
        assertFalse(gate.awaitRetired(1, timeoutMillis = 50))
        assertTrue(gate.awaitRetired(2, timeoutMillis = 1_000))
        assertNull(gate.admit(2))
        assertEquals(1, gate.admittedCount(1))

        gate.release(last)
        assertTrue(gate.awaitRetired(1, timeoutMillis = 1_000))
        assertTrue(gate.awaitRetired(1, timeoutMillis = 1_000))
        assertNull(gate.admit(1))
        gate.reopen(1)
        val successor = assertNotNull(gate.admit(1))
        gate.release(first)
        gate.release(last)
        assertEquals(1, gate.admittedCount(1))
        assertFalse(gate.awaitRetired(1, timeoutMillis = 50))
        gate.release(successor)
        assertEquals(0, gate.admittedCount(1))
    }

    @Test
    fun racingAdmissionCannotOutliveSuccessfulRetirement() = runBlocking {
        withTimeout(30_000) {
            val gate = UiAdmissionGate(Log.tag("SharedUiAdmissionGateTest"))
            val held = assertNotNull(async(Dispatchers.Default) { gate.admit(1) }.await())
            assertFalse(async(Dispatchers.Default) { gate.awaitRetired(1, 20) }.await())
            assertEquals(1, gate.admittedCount(1))
            gate.release(held)
            assertTrue(gate.awaitRetired(1, 1_000))

            assertTrue(async(Dispatchers.Default) { gate.awaitRetired(2, 1_000) }.await())
            assertNull(async(Dispatchers.Default) { gate.admit(2) }.await())

            var retireWins = 0
            repeat(64) { round ->
                if (race(gate, round + 100)) retireWins++
            }
            println("UI admission race: 64 rounds, retire=$retireWins, admit=${64 - retireWins}")
        }
    }

    private suspend fun race(gate: UiAdmissionGate, id: Int): Boolean = coroutineScope {
        val ready = Channel<Unit>(2)
        val start = CompletableDeferred<Unit>()
        val admission = async(Dispatchers.Default) {
            ready.send(Unit)
            start.await()
            gate.admit(id)
        }
        val retirement = async(Dispatchers.Default) {
            ready.send(Unit)
            start.await()
            gate.awaitRetired(id, timeoutMillis = 20)
        }
        repeat(2) { ready.receive() }
        start.complete(Unit)
        val retired = retirement.await()
        // Keep any winning grant until the retirement verdict has been captured.
        val token = admission.await()
        try {
            if (retired) {
                assertNull(token, "generation $id: retirement and a held grant are exclusive")
                assertEquals(0, gate.admittedCount(id))
                assertNull(gate.admit(id))
            } else {
                assertNotNull(token, "generation $id: admission must win a refused retirement")
                assertEquals(1, gate.admittedCount(id))
            }
        } finally {
            token?.let(gate::release)
            ready.close()
        }
        assertTrue(gate.awaitRetired(id, timeoutMillis = 1_000))
        assertEquals(0, gate.admittedCount(id))
        retired
    }
}
