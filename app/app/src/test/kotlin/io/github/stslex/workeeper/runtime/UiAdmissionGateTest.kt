// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * Direct pins for [UiAdmissionGate] (Round-3 blocker 4): admission is a TOKEN, not a count, so
 * release is ABA-safe; a retired generation refuses admission outright (its content must resolve
 * nothing); release is idempotent and releases exactly its own grant; and the retire CAS closes
 * admission atomically with the zero observation.
 */
internal class UiAdmissionGateTest {

    private val gate = UiAdmissionGate(Log.tag("UiAdmissionGateTest"))

    @Test
    fun `a retired generation refuses admission outright`() = runBlocking {
        assertTrue(gate.awaitRetired(id = 1, timeoutMillis = 1_000))

        assertNull(gate.admit(1), "a retired generation must hand out no token")
        assertEquals(0, gate.admittedCount(1))
    }

    @Test
    fun `reopen re-admits - the id grants tokens again`() = runBlocking {
        assertTrue(gate.awaitRetired(id = 1, timeoutMillis = 1_000))
        gate.reopen(1)

        val token = gate.admit(1)

        assertNotNull(token)
        assertEquals(1, gate.admittedCount(1))
        gate.release(requireNotNull(token))
        assertEquals(0, gate.admittedCount(1))
    }

    @Test
    fun `awaitRetired times out while a region still holds its token`() = runBlocking {
        val token = requireNotNull(gate.admit(7))

        assertFalse(gate.awaitRetired(id = 7, timeoutMillis = 50))

        assertEquals(1, gate.admittedCount(7), "timeout must not retire or drop the grant")
        gate.release(token)
    }

    @Test
    fun `release is idempotent and releases exactly its own token`() = runBlocking {
        val first = requireNotNull(gate.admit(3))
        val second = requireNotNull(gate.admit(3))
        assertEquals(2, gate.admittedCount(3))

        gate.release(first)
        gate.release(first) // idempotent — must not release `second` as collateral
        assertEquals(1, gate.admittedCount(3))

        gate.release(second)
        assertEquals(0, gate.admittedCount(3))
    }

    @Test
    fun `a token released after retirement and reopen cannot cancel a LATER admission (ABA)`() =
        runBlocking {
            // The ABA the counter form could not see: region A of generation 5 is admitted, the
            // generation is retired and later reopened (an aborted transition), a NEW region B
            // takes admission — and only then A's stale release lands. With a counter, A's
            // decrement would zero B's admission and let a transition close the database under a
            // live region. With tokens, A's serial is no longer live and the release is a no-op.
            val stale = requireNotNull(gate.admit(5))
            gate.release(stale)
            assertTrue(gate.awaitRetired(id = 5, timeoutMillis = 1_000))
            gate.reopen(5)
            val fresh = requireNotNull(gate.admit(5))

            gate.release(stale) // the late, already-spent grant

            assertEquals(1, gate.admittedCount(5), "the LATER region must still hold admission")
            assertFalse(
                gate.awaitRetired(id = 5, timeoutMillis = 50),
                "a stale release must not open the gate under a live region",
            )
            gate.release(fresh)
        }

    @Test
    fun `retire is ATOMIC with the zero observation - racing admissions never pass it (hammer)`() {
        // Invariant under attack: after awaitRetired returns true, the id holds no admission and
        // can never gain one. A two-step observe-then-retire gate lets a racer's admit land in
        // between — the id ends retired WITH a live token (whose release is then a no-op on a
        // retired id), which the assertion below detects.
        repeat(3_000) { iteration ->
            val id = iteration + 100
            val held = requireNotNull(gate.admit(id))
            val ready = CountDownLatch(1)
            val racer = thread {
                ready.countDown()
                gate.release(held)
                gate.admit(id)?.let(gate::release) // races the retire CAS
            }
            ready.await()
            val retired = runBlocking { gate.awaitRetired(id, timeoutMillis = 2_000) }
            racer.join()
            assertTrue(retired, "iteration $iteration: the gate must retire once idle")
            assertEquals(
                0,
                gate.admittedCount(id),
                "iteration $iteration: an admission passed the retired gate — the close is not atomic",
            )
            assertNull(gate.admit(id), "iteration $iteration: a retired id must stay closed")
        }
    }
}
