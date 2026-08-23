// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

/**
 * Direct pins for [UiAdmissionGate]'s atomic zero-observation + retire close (round-2 mandate 7).
 * The deterministic tests pin the refusal/reopen semantics; the multi-threaded hammer pins the
 * ATOMICITY itself — an implementation that observes zero and retires in two separate steps lets
 * a racing attach land in the gap (counted under a retired id), which the invariant assertion
 * catches with overwhelming probability across the iterations.
 */
internal class UiAdmissionGateTest {

    private val gate = UiAdmissionGate(Log.tag("UiAdmissionGateTest"))

    @Test
    fun `attach against a retired id is refused - never counted`() = runBlocking {
        assertTrue(gate.awaitRetired(id = 1, timeoutMillis = 1_000))

        gate.attach(1)

        assertEquals(0, gate.attachmentCount(1), "a retired id must refuse attaches")
    }

    @Test
    fun `reopen un-retires - the id counts attaches again`() = runBlocking {
        assertTrue(gate.awaitRetired(id = 1, timeoutMillis = 1_000))
        gate.reopen(1)

        gate.attach(1)

        assertEquals(1, gate.attachmentCount(1))
        gate.dispose(1)
    }

    @Test
    fun `awaitRetired times out while an attachment is outstanding`() = runBlocking {
        gate.attach(7)

        assertFalse(gate.awaitRetired(id = 7, timeoutMillis = 50))

        assertEquals(1, gate.attachmentCount(7), "timeout must not retire or consume the count")
        gate.dispose(7)
    }

    @Test
    fun `retire is ATOMIC with the zero observation - racing attaches never pass it (hammer)`() {
        // Invariant under attack: after awaitRetired returns true, the id's count is zero and
        // stays zero — an attach that raced the retire either landed BEFORE the CAS (count > 0,
        // the retire re-waits) or AFTER it (refused). A two-step observe-then-retire gate lets
        // the racer's attach land in between: the id ends retired WITH a counted attachment
        // (its dispose is a no-op on a retired id), which the assertion below detects.
        repeat(4_000) { iteration ->
            val id = iteration + 100
            gate.attach(id)
            val racer = thread {
                gate.dispose(id)
                gate.attach(id) // races the retire CAS
                gate.dispose(id) // balances if the attach was counted pre-retire
            }
            val retired = runBlocking { gate.awaitRetired(id, timeoutMillis = 2_000) }
            racer.join()
            assertTrue(retired, "iteration $iteration: the gate must retire once idle")
            assertEquals(
                0,
                gate.attachmentCount(id),
                "iteration $iteration: an attach passed the retired gate — the close is not atomic",
            )
        }
    }
}
