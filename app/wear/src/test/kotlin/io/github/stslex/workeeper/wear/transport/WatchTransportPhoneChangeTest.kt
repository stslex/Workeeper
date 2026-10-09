// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.WearEnvelope
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.WatchActionResult
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.ui.surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * wear-live-sync.md §7.3 and §10.1, watch side: origin O6, the phone's change signal, through the
 * real owner and coordinator against a phone that answers with the real codec, on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WatchTransportPhoneChangeTest {

    @Test
    fun `an O6 while interactive and idle starts one handshake`() = runTest {
        val h = connected(this)

        h.runtime.onPhoneChanged()
        runCurrent()

        assertEquals(2, h.link.handshakes.size)
    }

    @Test
    fun `an O6 while not interactive starts one handshake`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })

        h.runtime.onPhoneChanged()
        runCurrent()

        assertEquals(1, h.link.handshakes.size, "O6 runs in ambient and with no Activity at all")
    }

    @Test
    fun `an O6 during an in-flight handshake starts exactly one more after it`() = runTest {
        val h = TransportHarness(this)
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onControllerInteractive(true)
        runCurrent()

        h.runtime.onPhoneChanged()
        runCurrent()
        assertEquals(1, h.link.handshakes.size, "nothing starts beside the request in flight")

        h.link.gate = null
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, h.link.handshakes.size, "the change that arrived meanwhile is asked for")

        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()
        assertEquals(2, h.link.handshakes.size)
    }

    @Test
    fun `an O6 while a refresh is queued starts no extra one`() = runTest {
        val h = connected(this)
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(revision = 2, leaseGeneration = 2) })
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        h.coordinator.requestUserRefresh()
        h.runtime.onPhoneChanged()
        runCurrent()

        h.link.gate = null
        gate.complete(Unit)
        runCurrent()
        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()

        assertEquals(
            listOf("handshake", "command", "handshake"),
            h.link.requests.map { it.kind() },
            "the queued refresh started after the signal, so its answer carries the change",
        )
    }

    @Test
    fun `an O6 during an in-flight command starts one handshake after the command`() = runTest {
        val h = connected(this)
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(revision = 2, leaseGeneration = 2) })
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        h.runtime.onPhoneChanged()
        runCurrent()
        assertEquals(CommandStatus.IN_FLIGHT, h.owner.snapshot.value.workout.command?.status, "no token retired it")

        h.link.gate = null
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("handshake", "command", "handshake"), h.link.requests.map { it.kind() })
        assertEquals(CommandStatus.TERMINAL, h.owner.snapshot.value.workout.command?.status)
    }

    @Test
    fun `under retry preservation an O6 waits for the binding's deadline, then starts one handshake`() = runTest {
        val h = connected(this)
        h.runtime.onControllerInteractive(false)
        h.link.gate = CompletableDeferred()
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        advanceTimeBy(REQUEST_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(CommandStatus.TIMED_OUT_RETRYABLE, h.owner.snapshot.value.workout.command?.status)
        val binding = h.owner.snapshot.value.workout.authority as LocalMutationAuthority.AttemptBound
        h.link.gate = null
        val handshakesBefore = h.link.handshakes.size

        h.runtime.onPhoneChanged()
        runCurrent()
        assertEquals(handshakesBefore, h.link.handshakes.size, "the user's Retry decides while the binding holds")

        advanceTimeBy(binding.effectiveDeadlineMs - h.clock.nowMs() - 1)
        runCurrent()
        assertEquals(handshakesBefore, h.link.handshakes.size, "nothing before the deadline")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(handshakesBefore + 1, h.link.handshakes.size, "the deferral served the change at the deadline")
    }

    @Test
    fun `with the automatic budget spent an O6 still starts`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        repeat(AUTO_REFRESH_BUDGET + 1) {
            h.runtime.onControllerInteractive(false)
            h.runtime.onControllerInteractive(true)
            runCurrent()
        }
        assertEquals(AUTO_REFRESH_BUDGET, h.link.handshakes.size, "the budget is spent")

        h.runtime.onPhoneChanged()
        runCurrent()

        assertEquals(AUTO_REFRESH_BUDGET + 1, h.link.handshakes.size, "O6 is neither counted nor limited by it")
    }

    /** The other half of "neither counted nor limited": O6 handshakes leave the whole budget to O1–O4. */
    @Test
    fun `O6 handshakes do not count against the automatic budget`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        repeat(PHONE_CHANGE_BURST) {
            h.runtime.onPhoneChanged()
            runCurrent()
        }
        val afterPhoneChanges = h.link.handshakes.size

        repeat(AUTO_REFRESH_BUDGET) {
            h.runtime.onControllerInteractive(false)
            h.runtime.onControllerInteractive(true)
            runCurrent()
        }

        assertEquals(
            afterPhoneChanges + AUTO_REFRESH_BUDGET,
            h.link.handshakes.size,
            "every O1 within the window still starts after the O6 handshakes",
        )
    }

    @Test
    fun `the bucket starts ten O6 handshakes at once and the eleventh one refill period after the first`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        repeat(PHONE_CHANGE_BURST) {
            h.runtime.onPhoneChanged()
            runCurrent()
        }
        assertEquals(PHONE_CHANGE_BURST, h.link.handshakes.size, "ten without waiting")

        h.runtime.onPhoneChanged()
        runCurrent()
        val calls = h.link.calls.size
        val lines = h.logger.lines.size
        advanceTimeBy(PHONE_CHANGE_REFILL_MS - 1)
        runCurrent()
        assertEquals(PHONE_CHANGE_BURST, h.link.handshakes.size, "the eleventh waits for a token")
        assertEquals(calls, h.link.calls.size, "while it waits the coordinator calls nothing")
        assertEquals(lines, h.logger.lines.size, "and runs no work that logs")

        advanceTimeBy(1)
        runCurrent()
        assertEquals(PHONE_CHANGE_BURST + 1, h.link.handshakes.size, "one refill period after the first")
    }

    @Test
    fun `an O6 handshake that finds no reachable phone is not retried`() = runTest {
        val h = TransportHarness(this)
        h.link.phones = emptyList()

        h.runtime.onPhoneChanged()
        runCurrent()
        val calls = h.link.calls.toList()
        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()

        assertEquals(1, calls.count { it == "reachablePhones" }, "one lookup")
        assertEquals(calls, h.link.calls, "no retry")
        assertEquals(0, h.link.requests.size)
    }

    @Test
    fun `an O6 chain answered Unavailable gets one follow-up while interactive`() = runTest {
        val h = connected(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(unavailable = true) })

        h.runtime.onPhoneChanged()
        runCurrent()

        assertEquals(3, h.link.handshakes.size, "the connect, the O6 and its one follow-up")
    }

    @Test
    fun `an O6 chain answered Unavailable gets no follow-up while not interactive`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(unavailable = true) })

        h.runtime.onPhoneChanged()
        runCurrent()
        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()

        assertEquals(1, h.link.handshakes.size, "like O2 and O4, no follow-up outside interaction")
    }

    @Test
    fun `a draft survives an O6 handshake at the same revision and is cleared at a new one`() = runTest {
        val h = connected(this)
        assertEquals(WatchActionResult.Updated, h.runtime.onAction(ControllerAction.SetReps(12)))
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(leaseGeneration = 2) })

        h.runtime.onPhoneChanged()
        runCurrent()
        assertEquals(12, h.owner.surface.value.reps, "same source version: the draft stays (F18)")
        assertTrue(h.owner.surface.value.hasUnsubmittedDraft)

        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(revision = 2, leaseGeneration = 3) })
        h.runtime.onPhoneChanged()
        runCurrent()
        assertEquals(8, h.owner.surface.value.reps, "a new revision clears it")
        assertTrue(!h.owner.surface.value.hasUnsubmittedDraft)
    }

    /** Rule 4: a queued refresh of another origin dropped at start serves the pending change. */
    @Test
    fun `an O1 behind a command that times out is dropped and the change starts one O6 at the deadline`() = runTest {
        val h = connected(this)
        h.link.gate = CompletableDeferred()
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        h.runtime.onControllerInteractive(false)
        h.runtime.onControllerInteractive(true)
        h.runtime.onPhoneChanged()
        runCurrent()
        assertEquals(CommandStatus.IN_FLIGHT, h.owner.snapshot.value.workout.command?.status)
        val handshakesBefore = h.link.handshakes.size

        advanceTimeBy(REQUEST_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(CommandStatus.TIMED_OUT_RETRYABLE, h.owner.snapshot.value.workout.command?.status)
        assertTrue(
            "refresh CONTROLLER_INTERACTIVE dropped: retry preserved" in h.logger.lines,
            "the queued O1 was dropped at start: ${h.logger.lines}",
        )
        val binding = h.owner.snapshot.value.workout.authority as LocalMutationAuthority.AttemptBound
        h.link.gate = null

        advanceTimeBy(binding.effectiveDeadlineMs - h.clock.nowMs() - 1)
        runCurrent()
        assertEquals(handshakesBefore, h.link.handshakes.size, "nothing before the binding's deadline")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(handshakesBefore + 1, h.link.handshakes.size, "exactly one O6 at the binding's deadline")
    }

    /** Rule 3: an O6 that cannot get its token gives its change up; the next signal recovers it. */
    @Test
    fun `an O6 whose token cannot be issued is dropped once and only the next signal starts one`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.env.failNextId = true

        h.runtime.onPhoneChanged()
        runCurrent()
        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()
        assertEquals(
            listOf("refresh phone_changed dropped: no token"),
            h.logger.lines.filter { "dropped" in it },
            "dropped once, and said so",
        )
        assertEquals(0, h.link.handshakes.size, "no handshake until the next signal")

        h.runtime.onPhoneChanged()
        runCurrent()

        assertEquals(1, h.link.handshakes.size, "the next signal starts one")
    }

    /** Rule 6: an O6 chain's follow-up is an ordinary automatic follow-up, under the budget. */
    @Test
    fun `with the automatic budget spent an O6 chain answered Unavailable while interactive gets no follow-up`() =
        runTest {
            val h = TransportHarness(this)
            h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
            repeat(AUTO_REFRESH_BUDGET) {
                h.runtime.onControllerInteractive(false)
                h.runtime.onControllerInteractive(true)
                runCurrent()
            }
            assertEquals(AUTO_REFRESH_BUDGET, h.link.handshakes.size, "the budget is spent")
            h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(unavailable = true) })

            h.runtime.onPhoneChanged()
            runCurrent()

            assertEquals(AUTO_REFRESH_BUDGET + 1, h.link.handshakes.size, "the O6 and no follow-up")
            assertTrue("refresh phone_changed dropped: budget" in h.logger.lines, "${h.logger.lines}")
        }

    /** Rule 5: the deferral is the only timer; nothing polls while a change waits for a token. */
    @Test
    fun `an O6 waiting for a token runs no work until the deferral fires once`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        repeat(PHONE_CHANGE_BURST) {
            h.runtime.onPhoneChanged()
            runCurrent()
        }
        h.runtime.onPhoneChanged()
        runCurrent()
        val reads = h.clockReads

        advanceTimeBy(PHONE_CHANGE_REFILL_MS - 1)
        runCurrent()
        assertEquals(reads, h.clockReads, "nothing read the clock while the change waited")
        assertEquals(PHONE_CHANGE_BURST, h.link.handshakes.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(PHONE_CHANGE_BURST + 1, h.link.handshakes.size, "the deferral fired and served the change")
    }

    @Test
    fun `the refill period runs from the first take of a full bucket, not from the last take`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onPhoneChanged()
        runCurrent()
        val t0 = h.clock.nowMs()
        advanceTimeBy(FOUR_SECONDS_MS)
        repeat(PHONE_CHANGE_BURST - 1) {
            h.runtime.onPhoneChanged()
            runCurrent()
        }
        assertEquals(PHONE_CHANGE_BURST, h.link.handshakes.size, "each started after the previous one completed")

        h.runtime.onPhoneChanged()
        runCurrent()
        advanceTimeBy(t0 + PHONE_CHANGE_REFILL_MS - h.clock.nowMs() - 1)
        runCurrent()
        assertEquals(PHONE_CHANGE_BURST, h.link.handshakes.size, "the eleventh waits for the first take's period")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PHONE_CHANGE_BURST + 1, h.link.handshakes.size, "it starts at t0 + 10 s, not at t0 + 14 s")
    }

    /** Interactive, with one accepted handshake and fresh authority. */
    private fun connected(scope: TestScope): TransportHarness = TransportHarness(scope).also { h ->
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onControllerInteractive(true)
        scope.runCurrent()
        assertInstanceOf(LocalMutationAuthority.Available::class.java, h.owner.snapshot.value.workout.authority)
    }

    private fun WearEnvelope.kind(): String = if (this is GetActiveWorkoutRequest) "handshake" else "command"

    private companion object {
        const val FIVE_MINUTES_MS = 300_000L
        const val FOUR_SECONDS_MS = 4_000L
    }
}
