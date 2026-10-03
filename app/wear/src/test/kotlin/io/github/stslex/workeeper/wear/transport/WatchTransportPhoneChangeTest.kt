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
    }
}
