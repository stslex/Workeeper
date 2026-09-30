// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import io.github.stslex.workeeper.core.wear.protocol.ActiveTarget
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearEnvelope
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.wear.cache.CacheFraming
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.LinkStatus
import io.github.stslex.workeeper.wear.runtime.WatchActionResult
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * wear-paired-transport.md §10.1, watch side: the coordinator and the real owner against a phone
 * that answers with the real codec, on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WatchTransportCoordinatorTest {

    // region handshake

    @Test
    fun `an accepted handshake installs fresh authority`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })

        h.runtime.onControllerInteractive(true)
        runCurrent()

        assertEquals(1, h.link.handshakes.size)
        val workout = h.owner.snapshot.value.workout
        assertEquals(ActiveFreshness.FRESH, (workout.display as WatchDisplayState.Active).freshness)
        assertInstanceOf(LocalMutationAuthority.Available::class.java, workout.authority)
        assertEquals(LinkStatus.REACHABLE, h.owner.snapshot.value.link)
    }

    @Test
    fun `nothing reaches the link before a request origin`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })

        h.runtime.onWake()
        h.runtime.setLocale(Locale.US)
        assertEquals(WatchActionResult.Rejected, h.runtime.onAction(ControllerAction.CompleteSet))
        h.runtime.onControllerInteractive(false)
        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()
        // §7.7: in production every link call is a Play services call.
        assertEquals(emptyList<String>(), h.link.calls, "no origin, no link call")

        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(listOf("reachablePhones", "observeReachability", "localNodeId", "request"), h.link.calls)
    }

    @Test
    fun `from Loading, no reachable phone shows the retry surface and Retry refreshes`() = runTest {
        val h = TransportHarness(this)
        h.link.phones = emptyList()

        h.runtime.onControllerInteractive(true)
        runCurrent()

        assertEquals(0, h.link.requests.size, "no request is sent without a reachable phone")
        assertEquals(LinkStatus.UNREACHABLE, h.owner.snapshot.value.link)
        assertRetrySurface(h)

        h.link.phones = listOf(PhoneNode(FakePhoneLink.PHONE_NODE, isNearby = true))
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        assertEquals(WatchActionResult.RefreshRequested, h.runtime.onAction(ControllerAction.Retry))
        runCurrent()

        assertEquals(1, h.link.handshakes.size)
        assertInstanceOf(WatchDisplayState.Active::class.java, h.owner.snapshot.value.workout.display)
    }

    @Test
    fun `from Loading, an unanswered handshake shows the retry surface and Retry refreshes`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = { ByteArray(0) }

        h.runtime.onControllerInteractive(true)
        runCurrent()

        assertEquals(1, h.link.handshakes.size)
        assertEquals(LinkStatus.UNANSWERED, h.owner.snapshot.value.link)
        assertRetrySurface(h)

        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        assertEquals(WatchActionResult.RefreshRequested, h.runtime.onAction(ControllerAction.Retry))
        runCurrent()

        assertEquals(2, h.link.handshakes.size)
        assertInstanceOf(WatchDisplayState.Active::class.java, h.owner.snapshot.value.workout.display)
    }

    @Test
    fun `undecodable bytes fail closed with no retry and no follow-up`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertTrue(h.owner.snapshot.value.ongoing is OngoingStatus.Scheduled, "the fresh grant posted an ongoing")

        h.link.answer = { "not a protocol envelope".toByteArray() }
        h.coordinator.requestUserRefresh()
        runCurrent()

        assertProtocolMismatch(h)
        assertEquals(OngoingStatus.Inactive, h.owner.snapshot.value.ongoing, "the ongoing surface stopped")
        assertEquals(2, h.link.handshakes.size, "no follow-up while the display is a protocol mismatch")
    }

    @Test
    fun `the other operation's shape for the request's correlation is a protocol failure`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = { request ->
            WearProtocolCodec.encode(
                CompleteCurrentSetResponse(
                    schemaVersion = WearProtocol.SCHEMA_VERSION,
                    correlationId = request.correlationId,
                    commandId = ReducerTestFixtures.commandId,
                    outcome = CompleteCommandOutcome.Applied,
                    replacement = ReducerTestFixtures.active(),
                ),
            )
        }

        h.runtime.onControllerInteractive(true)
        runCurrent()

        assertProtocolMismatch(h)
        assertEquals(1, h.link.handshakes.size)
    }

    @Test
    fun `a response for another correlation is no response`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = {
            WearProtocolCodec.encode(
                ActiveWorkoutSnapshotResponse(
                    WearProtocol.SCHEMA_VERSION,
                    CanonicalUuid.random(),
                    ReducerTestFixtures.active(),
                ),
            )
        }

        h.runtime.onControllerInteractive(true)
        runCurrent()

        assertEquals(WatchDisplayState.Loading, h.owner.snapshot.value.workout.display)
        assertEquals(LinkStatus.UNANSWERED, h.owner.snapshot.value.link)
        assertRetrySurface(h)
    }

    // endregion

    // region commands

    @Test
    fun `an applied command advances the set from the attached snapshot`() = runTest {
        val h = connected(this)
        val stopBefore = (h.owner.snapshot.value.ongoing as OngoingStatus.Scheduled).stopAtElapsedRealtimeMs
        val next = ReducerTestFixtures.active(revision = 2, leaseGeneration = 2, targetPosition = 1)
        h.link.answer = phoneAnswers(snapshot = { next }, outcome = { CompleteCommandOutcome.Applied })
        advanceTimeBy(FIVE_SECONDS_MS)

        assertInstanceOf(WatchActionResult.CommandIssued::class.java, h.runtime.onAction(ControllerAction.CompleteSet))
        runCurrent()

        val cached = requireNotNull(CacheFraming.decode(requireNotNull(h.env.storage.read())))
        val decodedCache = WearProtocolCodec.decodeForWatch(requireNotNull(cached.payload)) as WatchDecodeResult.Success
        val cachedSnapshot = decodedCache.envelope as ActiveWorkoutSnapshotResponse
        assertEquals(next, cachedSnapshot.snapshot, "the cache holds the attached snapshot")
        val stopAfter = (h.owner.snapshot.value.ongoing as OngoingStatus.Scheduled).stopAtElapsedRealtimeMs
        assertEquals(FIVE_SECONDS_MS, stopAfter - stopBefore, "the ongoing deadline follows the response")

        val command = h.link.commands.single()
        assertEquals(0, command.body.setPosition)
        val workout = h.owner.snapshot.value.workout
        val display = workout.display as WatchDisplayState.Active
        assertEquals(1, (display.snapshot.payload as SnapshotPayload.ActiveWithTarget).target.setPosition)
        assertEquals(CommandStatus.TERMINAL, workout.command?.status)
        assertTrue(h.owner.snapshot.value.ongoing is OngoingStatus.Scheduled, "ongoing follows the attached snapshot")
        assertTrue(h.env.storage.read() != null, "the attached snapshot was cached")
        assertEquals(1, h.link.handshakes.size, "a granted successor needs no follow-up")
    }

    @Test
    fun `a timed out command retries once with the same command id and then abandons`() = runTest {
        val h = connected(this)
        h.link.gate = CompletableDeferred()

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        advanceTimeBy(REQUEST_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(CommandStatus.TIMED_OUT_RETRYABLE, h.owner.snapshot.value.workout.command?.status)
        assertEquals(1, h.link.handshakes.size, "a first timeout starts no refresh: the user's Retry decides")

        assertInstanceOf(WatchActionResult.CommandIssued::class.java, h.runtime.onAction(ControllerAction.Retry))
        runCurrent()
        h.link.gate = null
        advanceTimeBy(REQUEST_TIMEOUT_MS + 1)
        runCurrent()

        val (first, second) = h.link.commands
        assertEquals(first.commandId, second.commandId)
        assertNotEquals(first.correlationId, second.correlationId)
        assertEquals(CommandStatus.ABANDONED, h.owner.snapshot.value.workout.command?.status)
        assertEquals(2, h.link.handshakes.size, "exactly one follow-up refresh after abandoning")

        assertEquals(WatchActionResult.Rejected, h.runtime.onAction(ControllerAction.Retry))
        runCurrent()
        assertEquals(2, h.link.commands.size, "never a third attempt")
    }

    @Test
    fun `a typed retryable outcome rebinds and Retry sends the rebound attempt`() = runTest {
        val h = connected(this)
        val successor = ReducerTestFixtures.active(leaseId = ReducerTestFixtures.lease2, leaseGeneration = 2)
        h.link.answer = phoneAnswers(
            snapshot = { successor },
            outcome = { CompleteCommandOutcome.RetryableTemporaryFailure },
        )

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        assertEquals(CommandStatus.RETRY_READY, h.owner.snapshot.value.workout.command?.status)

        h.link.answer = phoneAnswers(snapshot = { successor }, outcome = { CompleteCommandOutcome.Applied })
        assertInstanceOf(WatchActionResult.CommandIssued::class.java, h.runtime.onAction(ControllerAction.Retry))
        runCurrent()

        val (first, rebound) = h.link.commands
        assertEquals(first.commandId, rebound.commandId)
        assertEquals(ReducerTestFixtures.lease1, first.mutationLeaseId)
        assertEquals(ReducerTestFixtures.lease2, rebound.mutationLeaseId)
        assertEquals(2L, rebound.mutationLeaseGeneration)
    }

    // endregion

    // region coordinator invariants

    @Test
    fun `a refresh requested during a command gets its token only after the command`() = runTest {
        val h = connected(this)
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(revision = 2, leaseGeneration = 2) })

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        h.coordinator.requestUserRefresh()
        runCurrent()

        val during = h.owner.snapshot.value.workout
        assertEquals(CommandStatus.IN_FLIGHT, during.command?.status, "no handshake token retired the attempt")
        assertInstanceOf(LocalMutationAuthority.AttemptBound::class.java, during.authority)

        h.link.gate = null
        gate.complete(Unit)
        runCurrent()

        assertEquals(
            listOf("handshake", "command", "handshake"),
            h.link.requests.map { if (it is GetActiveWorkoutRequest) "handshake" else "command" },
        )
    }

    @Test
    fun `a queued automatic refresh that would start while the command is retryable is dropped`() = runTest {
        val h = connected(this)
        h.link.gate = CompletableDeferred()

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        // Queued while the command is in flight: at request time it is not yet retryable.
        h.runtime.onControllerInteractive(false)
        h.runtime.onControllerInteractive(true)
        runCurrent()
        advanceTimeBy(REQUEST_TIMEOUT_MS + 1)
        runCurrent()

        assertEquals(CommandStatus.TIMED_OUT_RETRYABLE, h.owner.snapshot.value.workout.command?.status)
        assertEquals(1, h.link.handshakes.size, "the automatic refresh was dropped when it would have started")
        assertInstanceOf(WatchActionResult.CommandIssued::class.java, h.runtime.onAction(ControllerAction.Retry))
    }

    @Test
    fun `at most one request is in flight`() = runTest {
        val h = connected(this)
        h.link.gate = CompletableDeferred()

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        // The owner's view of the command changes while its request is still out, so only the
        // in-flight slot keeps a user refresh from starting beside it.
        h.owner.transportTimeout(h.link.commands.single().correlationId)
        h.coordinator.requestUserRefresh()
        h.coordinator.onTileRendered()
        runCurrent()

        assertEquals(2, h.link.requests.size, "the first handshake, then only the command")
    }

    @Test
    fun `refreshes coalesce while a handshake is queued or in flight`() = runTest {
        val h = TransportHarness(this)
        h.link.gate = CompletableDeferred()
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })

        h.runtime.onControllerInteractive(true)
        runCurrent()
        h.coordinator.requestUserRefresh()
        h.coordinator.onTileRendered()
        runCurrent()
        h.link.gate?.complete(Unit)
        runCurrent()

        assertEquals(1, h.link.handshakes.size)
    }

    @Test
    fun `a phone that always answers Unavailable costs exactly two handshakes per origin`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(unavailable = true) })

        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(2, h.link.handshakes.size, "the origin and its one follow-up")

        advanceTimeBy(FIVE_MINUTES_MS)
        runCurrent()
        assertEquals(2, h.link.handshakes.size, "nothing without a new origin")

        h.runtime.onControllerInteractive(false)
        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(4, h.link.handshakes.size)
    }

    @Test
    fun `automatic handshakes stop at the budget and user refreshes are not limited`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })

        repeat(AUTO_REFRESH_BUDGET + 3) {
            h.runtime.onControllerInteractive(false)
            h.runtime.onControllerInteractive(true)
            runCurrent()
        }
        assertEquals(AUTO_REFRESH_BUDGET, h.link.handshakes.size)

        h.coordinator.requestUserRefresh()
        runCurrent()
        assertEquals(AUTO_REFRESH_BUDGET + 1, h.link.handshakes.size, "a user refresh is not limited")

        advanceTimeBy(AUTO_REFRESH_WINDOW_MS)
        h.runtime.onControllerInteractive(false)
        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(AUTO_REFRESH_BUDGET + 2, h.link.handshakes.size, "the window rolls")
    }

    @Test
    fun `a Tile render refreshes only when no handshake completed within the minimum age`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })

        h.coordinator.onTileRendered()
        runCurrent()
        assertEquals(1, h.link.handshakes.size, "no handshake completed yet")

        advanceTimeBy(TILE_REFRESH_MIN_AGE_MS - 1)
        h.coordinator.onTileRendered()
        runCurrent()
        assertEquals(1, h.link.handshakes.size, "a render inside the minimum age does not refresh")

        advanceTimeBy(1)
        h.coordinator.onTileRendered()
        runCurrent()
        assertEquals(2, h.link.handshakes.size)
    }

    @Test
    fun `phone reachability starts a refresh only while the controller is interactive`() = runTest {
        val h = connected(this)
        val reachability = requireNotNull(h.link.reachability) { "the first request registers the observer" }
        h.runtime.onControllerInteractive(false)
        runCurrent()

        reachability(false)
        runCurrent()
        assertEquals(ActiveFreshness.DISCONNECTED, h.freshness())
        reachability(true)
        runCurrent()
        assertEquals(1, h.link.handshakes.size, "no O3 while not interactive")

        h.link.phones = emptyList()
        reachability(false)
        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(1, h.link.handshakes.size, "O1 found no phone: nothing was sent")
        h.link.phones = listOf(PhoneNode(FakePhoneLink.PHONE_NODE, isNearby = true))
        reachability(true)
        runCurrent()
        assertEquals(2, h.link.handshakes.size, "O3: from none to some while interactive")
    }

    @Test
    fun `a phone that becomes reachable after an unreachable first request is O3`() = runTest {
        val h = TransportHarness(this)
        h.link.phones = emptyList()
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertRetrySurface(h)

        h.link.phones = listOf(PhoneNode(FakePhoneLink.PHONE_NODE, isNearby = true))
        requireNotNull(h.link.reachability).invoke(true)
        runCurrent()

        assertEquals(1, h.link.handshakes.size)
        assertInstanceOf(WatchDisplayState.Active::class.java, h.owner.snapshot.value.workout.display)
    }

    @Test
    fun `authority expiry starts a refresh only while the controller is interactive`() = runTest {
        val h = connected(this)
        // Like the phone, every handshake grants a successor lease generation.
        var generation = 1L
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(leaseGeneration = ++generation) })
        h.runtime.onControllerInteractive(false)
        runCurrent()

        advanceTimeBy(WearProtocol.MAX_MUTATION_WINDOW_MS)
        h.owner.onWake()
        runCurrent()
        assertEquals(ActiveFreshness.STALE, h.freshness())
        assertEquals(1, h.link.handshakes.size, "no O4 while not interactive")

        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(2, h.link.handshakes.size, "O1")
        advanceTimeBy(WearProtocol.MAX_MUTATION_WINDOW_MS)
        h.owner.onWake()
        runCurrent()
        assertEquals(3, h.link.handshakes.size, "O4: fresh to stale while interactive")
    }

    @Test
    fun `node choice prefers a nearby node and breaks ties on the smallest id`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.link.phones = listOf(PhoneNode("b-relay", false), PhoneNode("c-near", true), PhoneNode("a-relay", false))
        h.coordinator.requestUserRefresh()
        runCurrent()
        h.link.phones = listOf(PhoneNode("b-relay", false), PhoneNode("a-relay", false))
        h.coordinator.requestUserRefresh()
        runCurrent()

        assertEquals(listOf("c-near", "a-relay"), h.link.sentTo)
    }

    @Test
    fun `the release identity reports the resolved local node id and never sends it`() = runTest {
        val h = TransportHarness(this, nodeId = LocalNodeId())
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onControllerInteractive(true)
        runCurrent()

        assertEquals(FakePhoneLink.WATCH_NODE, h.localNode.value)
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        assertEquals(
            FakePhoneLink.WATCH_NODE,
            h.owner.snapshot.value.workout.command?.fingerprintCommand?.sourceNodeId,
        )
        val sent = h.link.rawRequests.map { it.decodeToString() }
        assertTrue(sent.any { "complete_current_set" in it }, "the command was sent: $sent")
        assertTrue(sent.none { FakePhoneLink.WATCH_NODE in it }, "the node id never travels")
    }

    @Test
    fun `transport logs carry operation, result class, byte counts and time only`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { sentinelSnapshot() })
        h.runtime.onControllerInteractive(true)
        runCurrent()
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        val lines = h.logger.lines
        assertTrue(lines.any { "handshake -> snapshot:ActiveWithTarget" in it }, "the handshake was logged: $lines")
        assertTrue(lines.any { "command -> response:Applied" in it }, "the command was logged: $lines")
        val forbidden = listOf(
            SENTINEL_TRAINING,
            SENTINEL_EXERCISE,
            "98765",
            "997",
            FakePhoneLink.PHONE_NODE,
            FakePhoneLink.WATCH_NODE,
            "{",
            "\"",
        )
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-")
        val leaks = lines.filter { line -> forbidden.any { it in line } || uuid.containsMatchIn(line) }
        assertTrue(leaks.isEmpty(), "a transport log line carries a forbidden field: $leaks")
    }

    @Test
    fun `a cancelled Task settles its request and the next one proceeds`() = runTest {
        val h = TransportHarness(this)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.link.failNextRequest = kotlinx.coroutines.CancellationException("the Play services Task was cancelled")

        h.runtime.onControllerInteractive(true)
        runCurrent()
        assertEquals(LinkStatus.UNANSWERED, h.owner.snapshot.value.link)

        h.coordinator.requestUserRefresh()
        runCurrent()
        assertEquals(2, h.link.rawRequests.size, "the coordinator is not stuck: a second request went out")
        assertInstanceOf(WatchDisplayState.Active::class.java, h.owner.snapshot.value.workout.display)
    }

    @Test
    fun `a refresh posted before a command is handed over waits and never retires the attempt`() = runTest {
        val h = connected(this)
        advanceTimeBy(TILE_REFRESH_MIN_AGE_MS)
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active(revision = 2, leaseGeneration = 2) })

        h.coordinator.onTileRendered()
        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        assertEquals(listOf("handshake", "command", "handshake"), h.link.requests.map { it.kind() })
        assertEquals(ReducerTestFixtures.lease1, h.link.commands.single().mutationLeaseId)
        assertEquals(CommandStatus.TERMINAL, h.owner.snapshot.value.workout.command?.status, "the command applied")
    }

    @Test
    fun `a command whose attempt was retired before sending is never sent`() = runTest {
        val h = connected(this)

        h.runtime.onAction(ControllerAction.CompleteSet)
        h.owner.disconnected()
        runCurrent()

        assertEquals(0, h.link.commands.size)
        assertEquals(CommandStatus.ABANDONED, h.owner.snapshot.value.workout.command?.status)
        assertEquals(2, h.link.handshakes.size, "one follow-up refresh shows the truth")
    }

    @Test
    fun `an undecodable command response closes the command and stops the ongoing surface`() = runTest {
        val h = connected(this)
        h.link.answer = { request ->
            if (request is GetActiveWorkoutRequest) phoneAnswers({ ReducerTestFixtures.active() })(request)
            else "not a protocol envelope".toByteArray()
        }

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        assertProtocolMismatch(h)
        assertEquals(CommandStatus.TERMINAL, h.owner.snapshot.value.workout.command?.status)
        assertEquals(OngoingStatus.Inactive, h.owner.snapshot.value.ongoing)
        assertEquals(1, h.link.handshakes.size, "no follow-up while the display is a protocol mismatch")
    }

    @Test
    fun `a snapshot answering a command is a protocol failure for that command`() = runTest {
        val h = connected(this)
        h.link.answer = { request ->
            WearProtocolCodec.encode(
                ActiveWorkoutSnapshotResponse(
                    WearProtocol.SCHEMA_VERSION,
                    request.correlationId,
                    ReducerTestFixtures.active(),
                ),
            )
        }

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        assertProtocolMismatch(h)
        assertEquals(CommandStatus.TERMINAL, h.owner.snapshot.value.workout.command?.status)
        assertEquals(OngoingStatus.Inactive, h.owner.snapshot.value.ongoing)
    }

    @Test
    fun `a command response for another correlation or another command is no response`() = runTest {
        val h = connected(this)
        val replacement = ReducerTestFixtures.active(revision = 2, leaseGeneration = 2)
        h.link.answer = commandAnswer { request ->
            CompleteCurrentSetResponse(
                WearProtocol.SCHEMA_VERSION,
                CanonicalUuid.random(),
                request.commandId,
                CompleteCommandOutcome.Applied,
                replacement,
            )
        }

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()
        assertEquals(CommandStatus.TIMED_OUT_RETRYABLE, h.owner.snapshot.value.workout.command?.status)

        h.link.answer = commandAnswer { request ->
            CompleteCurrentSetResponse(
                WearProtocol.SCHEMA_VERSION,
                request.correlationId,
                CanonicalUuid.random(),
                CompleteCommandOutcome.Applied,
                replacement,
            )
        }
        assertInstanceOf(WatchActionResult.CommandIssued::class.java, h.runtime.onAction(ControllerAction.Retry))
        runCurrent()
        assertEquals(
            CommandStatus.ABANDONED,
            h.owner.snapshot.value.workout.command?.status,
            "a response for another command is not admitted either",
        )
    }

    @Test
    fun `an accepted Unavailable replacement starts exactly one follow-up`() = runTest {
        val h = connected(this)
        h.link.answer = phoneAnswers(
            snapshot = { ReducerTestFixtures.active(unavailable = true) },
            outcome = { CompleteCommandOutcome.AuthorizationExpired },
        )

        h.runtime.onAction(ControllerAction.CompleteSet)
        runCurrent()

        assertEquals(listOf("handshake", "command", "handshake"), h.link.requests.map { it.kind() })
    }

    // endregion

    /** Interactive, with one accepted handshake and fresh authority. */
    private fun connected(scope: TestScope): TransportHarness = TransportHarness(scope).also { h ->
        h.link.answer = phoneAnswers(snapshot = { ReducerTestFixtures.active() })
        h.runtime.onControllerInteractive(true)
        scope.runCurrent()
        check(h.owner.snapshot.value.workout.authority is LocalMutationAuthority.Available)
    }

    private fun TransportHarness.freshness(): ActiveFreshness =
        (owner.snapshot.value.workout.display as WatchDisplayState.Active).freshness

    private fun assertRetrySurface(h: TransportHarness) {
        val surface = WearSurfaceMapper.map(h.owner.snapshot.value)
        assertEquals(WearSurfaceKind.RETRYABLE_ERROR, surface.kind)
        assertTrue(surface.retryEnabled)
    }

    private fun assertProtocolMismatch(h: TransportHarness) {
        val snapshot = h.owner.snapshot.value
        assertInstanceOf(WatchDisplayState.ProtocolMismatch::class.java, snapshot.workout.display)
        assertEquals(LocalMutationAuthority.Retired, snapshot.workout.authority)
        val surface = WearSurfaceMapper.map(snapshot)
        assertEquals(WearSurfaceKind.PROTOCOL_MISMATCH, surface.kind)
        assertFalse(surface.retryEnabled)
        assertEquals(WatchActionResult.Rejected, h.runtime.onAction(ControllerAction.Retry))
    }

    private fun sentinelSnapshot(): SnapshotData = SnapshotData(
        databaseEpoch = ReducerTestFixtures.epoch,
        payload = SnapshotPayload.ActiveWithTarget(
            sessionUuid = ReducerTestFixtures.sessionA,
            sessionRevision = 1,
            trainingName = BoundedDisplayName.Value(SENTINEL_TRAINING),
            completedExercises = 0,
            totalExercises = 1,
            target = ActiveTarget(
                performedExerciseUuid = ReducerTestFixtures.exerciseA,
                exerciseName = BoundedDisplayName.Value(SENTINEL_EXERCISE),
                setPosition = 0,
                setOrdinal = 1,
                totalSets = 2,
                reps = 997,
                weightHundredthsKg = 98_765,
                exerciseType = ExerciseTypeWire.WEIGHTED,
                setType = SetTypeWire.WORK,
            ),
            mutationAuthority = MutationAuthority.Granted(
                ReducerTestFixtures.lease1,
                1,
                WearProtocol.MAX_MUTATION_WINDOW_MS,
            ),
        ),
    )

    private fun WearEnvelope.kind(): String = if (this is GetActiveWorkoutRequest) "handshake" else "command"

    /** Handshakes get the default snapshot; commands get [build]'s response. */
    private fun commandAnswer(
        build: (CompleteCurrentSetRequest) -> CompleteCurrentSetResponse,
    ): (WearEnvelope) -> ByteArray = { request ->
        if (request is CompleteCurrentSetRequest) {
            WearProtocolCodec.encode(build(request))
        } else {
            phoneAnswers(snapshot = { ReducerTestFixtures.active() })(request)
        }
    }

    private companion object {
        const val FIVE_SECONDS_MS = 5_000L
        const val FIVE_MINUTES_MS = 300_000L
        const val SENTINEL_TRAINING = "Sentinel-Training-7f3a"
        const val SENTINEL_EXERCISE = "Sentinel-Exercise-7f3a"
    }
}
