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
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Direct pins for [UiAdmissionGate]: admission is a TOKEN, not a count, so release is ABA-safe and
 * idempotent, and the retire CAS closes admission atomically with the zero observation.
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
            // With a counter, the stale release would zero the later region's admission and let a
            // transition close the database under it.
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
    fun `retire is ATOMIC with the zero observation - a grant and a clear verdict are exclusive`() {
        // A two-step observe-then-retire gate would hand out both, so the racer deliberately KEEPS
        // its token — counting after a cleanup could never observe the gap.
        repeat(500) { iteration ->
            val id = iteration + 100
            val held = requireNotNull(gate.admit(id))
            val racerToken = AtomicReference<UiAdmissionGate.Token?>(null)
            // Both sides wait on the SAME start signal so the window they race for is tight.
            val start = CountDownLatch(1)
            val racer = thread {
                start.await()
                gate.release(held)
                racerToken.set(gate.admit(id)) // races the retire; the grant is NOT released
            }
            start.countDown()
            // Short budget on purpose: the production budget would make the hammer take minutes.
            val retired = runBlocking { gate.awaitRetired(id, timeoutMillis = 20) }
            racer.join()

            if (retired) {
                assertNull(
                    racerToken.get(),
                    "iteration $iteration: a token was granted for a generation the gate " +
                        "reported clear — the retire is not atomic with the zero observation",
                )
                assertEquals(0, gate.admittedCount(id))
                assertNull(gate.admit(id), "iteration $iteration: a retired id must stay closed")
            } else {
                // The admit won: refusing to retire under a live grant is the other half.
                assertNotNull(racerToken.get(), "iteration $iteration: neither side made progress")
                gate.release(requireNotNull(racerToken.get()))
            }
        }
    }
}
