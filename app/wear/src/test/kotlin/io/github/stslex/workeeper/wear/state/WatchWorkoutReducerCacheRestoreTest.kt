// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class WatchWorkoutReducerCacheRestoreTest {
    @Test
    fun restoredGrantIsStrippedAndCannotIssueACommand() {
        val reducer = WatchWorkoutReducer()
        val cached = ReducerTestFixtures.active(revision = 9)
        reducer.restoreDisplayOnly(cached)
        val display = assertIs<WatchDisplayState.Active>(reducer.state.display)
        val payload = display.snapshot.payload as SnapshotPayload.ActiveWithTarget
        assertEquals(9L, payload.sessionRevision)
        assertEquals(8, payload.target.reps)
        assertEquals(ActiveFreshness.REFRESH_REQUIRED, display.freshness)
        assertEquals(
            MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED),
            payload.mutationAuthority,
        )
        assertIs<MutationAuthority.Granted>((cached.payload as SnapshotPayload.ActiveWithTarget).mutationAuthority)
        assertEquals(LocalMutationAuthority.Retired, reducer.state.authority)
        assertTrue(reducer.state.refreshRequired)
        assertNull(reducer.state.draft)
        assertNull(reducer.state.command)
        assertTrue(reducer.drainEvents().isEmpty())
        assertEquals(
            CommandIssueResult.Rejected,
            reducer.issueCommand(ReducerTestFixtures.id(1), 1_000, ReducerTestFixtures.fingerprint(revision = 9)),
        )
    }

    @Test
    fun restoredSourceMetadataRejectsOlderRevisions() {
        val reducer = WatchWorkoutReducer()
        reducer.restoreDisplayOnly(ReducerTestFixtures.active(revision = 9))
        assertFalse(reducer.receiveUnsolicited(ReducerTestFixtures.active(revision = 8)).accepted)
        val request = reducer.issueHandshake(ReducerTestFixtures.id(2), 1_000)
        assertFalse(
            reducer.receiveSnapshot(request.correlationId, ReducerTestFixtures.active(revision = 8), 1_001).accepted,
        )
        val display = assertIs<WatchDisplayState.Active>(reducer.state.display)
        assertEquals(9L, (display.snapshot.payload as SnapshotPayload.ActiveWithTarget).sessionRevision)
        assertEquals(LocalMutationAuthority.Retired, reducer.state.authority)
    }

    @Test
    fun onlyACorrelatedFreshHandshakeCanAuthorizeRestoredValues() {
        val reducer = WatchWorkoutReducer()
        reducer.restoreDisplayOnly(ReducerTestFixtures.active(revision = 9))
        assertTrue(reducer.receiveUnsolicited(ReducerTestFixtures.active(revision = 10, leaseGeneration = 2)).accepted)
        assertEquals(LocalMutationAuthority.Retired, reducer.state.authority)
        val request = reducer.issueHandshake(ReducerTestFixtures.id(3), 1_000)
        val result = reducer.receiveSnapshot(
            request.correlationId,
            ReducerTestFixtures.active(revision = 10, leaseGeneration = 3),
            1_001,
        )
        assertTrue(result.accepted)
        assertIs<LocalMutationAuthority.Available>(reducer.state.authority)
        assertFalse(reducer.state.refreshRequired)
    }

    @Test
    fun cacheRestoreCannotReplaceAnIssuedOrAlreadyRestoredReducer() {
        val issued = WatchWorkoutReducer()
        issued.issueHandshake(ReducerTestFixtures.id(4), 1_000)
        val beforeIssued = issued.state
        assertFailsWith<IllegalStateException> { issued.restoreDisplayOnly(ReducerTestFixtures.active()) }
        assertEquals(beforeIssued, issued.state)
        val restored = WatchWorkoutReducer()
        restored.restoreDisplayOnly(ReducerTestFixtures.active(revision = 9))
        val beforeRestored = restored.state
        assertFailsWith<IllegalStateException> { restored.restoreDisplayOnly(ReducerTestFixtures.active(revision = 8)) }
        assertEquals(beforeRestored, restored.state)
    }

    @Test
    fun unsolicitedAdmissionStillRejectsAnEmptyReducerAndForeignRestoredSource() {
        val empty = WatchWorkoutReducer()
        assertFalse(empty.receiveUnsolicited(ReducerTestFixtures.active()).accepted)
        assertEquals(WatchDisplayState.Loading, empty.state.display)
        val restored = WatchWorkoutReducer()
        restored.restoreDisplayOnly(ReducerTestFixtures.active())
        val foreignEpoch = ReducerTestFixtures.active(databaseEpoch = ReducerTestFixtures.otherEpoch)
        assertFalse(restored.receiveUnsolicited(foreignEpoch).accepted)
        assertFalse(
            restored.receiveUnsolicited(ReducerTestFixtures.active(session = ReducerTestFixtures.sessionB)).accepted,
        )
        assertEquals(LocalMutationAuthority.Retired, restored.state.authority)
    }
}
