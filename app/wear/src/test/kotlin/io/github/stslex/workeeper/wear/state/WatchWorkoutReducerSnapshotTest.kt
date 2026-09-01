// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchWorkoutReducerSnapshotTest {

    @Test
    fun `zero RTT is fresh at 119999 and stale at 120000`() {
        val reducer = WatchWorkoutReducer()
        val correlation = ReducerTestFixtures.id(1)
        reducer.issueHandshake(correlation, issuedAtElapsedRealtimeMs = 1_000)
        val reduction = reducer.receiveSnapshot(
            correlation,
            ReducerTestFixtures.active(),
            receivedAtElapsedRealtimeMs = 1_000,
        )

        assertEquals(120_000L, reduction.effectiveMutationWindowMs)
        assertIs<LocalMutationAuthority.Available>(reducer.state.authority)
        reducer.expireAuthority(nowElapsedRealtimeMs = 120_999)
        assertEquals(ActiveFreshness.FRESH, active(reducer).freshness)
        reducer.expireAuthority(nowElapsedRealtimeMs = 121_000)
        assertEquals(ActiveFreshness.STALE, active(reducer).freshness)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
    }

    @Test
    fun `nonzero RTT subtracts the whole round trip and zero remainder is read-only`() {
        val reducer = WatchWorkoutReducer()
        val first = ReducerTestFixtures.id(2)
        reducer.issueHandshake(first, issuedAtElapsedRealtimeMs = 1_000)
        val reduction = reducer.receiveSnapshot(
            first,
            ReducerTestFixtures.active(),
            receivedAtElapsedRealtimeMs = 11_000,
        )
        assertEquals(110_000L, reduction.effectiveMutationWindowMs)

        val second = ReducerTestFixtures.id(3)
        reducer.issueHandshake(second, issuedAtElapsedRealtimeMs = 20_000)
        val exhausted = reducer.receiveSnapshot(
            second,
            ReducerTestFixtures.active(leaseGeneration = 2, leaseId = ReducerTestFixtures.lease2),
            receivedAtElapsedRealtimeMs = 140_000,
        )
        assertNull(exhausted.effectiveMutationWindowMs)
        assertEquals(ActiveFreshness.REFRESH_REQUIRED, active(reducer).freshness)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
    }

    @Test
    fun `generation two retires generation one in both response orders`() {
        val reducer = WatchWorkoutReducer()
        val first = ReducerTestFixtures.id(4)
        val second = ReducerTestFixtures.id(5)
        reducer.issueHandshake(first, 0)
        reducer.issueHandshake(second, 1)

        assertFalse(
            reducer.receiveSnapshot(first, ReducerTestFixtures.active(), 2).accepted,
        )
        assertTrue(
            reducer.receiveSnapshot(
                second,
                ReducerTestFixtures.active(leaseGeneration = 2, leaseId = ReducerTestFixtures.lease2),
                3,
            ).accepted,
        )
        val available = assertIs<LocalMutationAuthority.Available>(reducer.state.authority)
        assertEquals(2, available.leaseGeneration)

        val reverse = WatchWorkoutReducer()
        val reverseFirst = ReducerTestFixtures.id(6)
        val reverseSecond = ReducerTestFixtures.id(7)
        reverse.issueHandshake(reverseFirst, 0)
        reverse.issueHandshake(reverseSecond, 1)
        assertTrue(
            reverse.receiveSnapshot(
                reverseSecond,
                ReducerTestFixtures.active(leaseGeneration = 2, leaseId = ReducerTestFixtures.lease2),
                2,
            ).accepted,
        )
        assertFalse(reverse.receiveSnapshot(reverseFirst, ReducerTestFixtures.active(), 3).accepted)
        assertEquals(
            2,
            assertIs<LocalMutationAuthority.Available>(reverse.state.authority).leaseGeneration,
        )
    }

    @Test
    fun `older request can advance a higher revision only as read-only`() {
        val reducer = WatchWorkoutReducer()
        val older = ReducerTestFixtures.id(8)
        val latest = ReducerTestFixtures.id(9)
        reducer.issueHandshake(older, 0)
        reducer.issueHandshake(latest, 1)
        reducer.receiveSnapshot(
            latest,
            ReducerTestFixtures.active(revision = 10, leaseGeneration = 2),
            2,
        )

        val result = reducer.receiveSnapshot(
            older,
            ReducerTestFixtures.active(revision = 11, leaseGeneration = 3),
            3,
        )
        assertTrue(result.accepted)
        assertNull(result.effectiveMutationWindowMs)
        assertEquals(11, activePayload(reducer).sessionRevision)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
    }

    @Test
    fun `only latest generation crosses active identity despite inverted revisions`() {
        val reducer = WatchWorkoutReducer()
        val a = ReducerTestFixtures.id(10)
        reducer.issueHandshake(a, 0)
        reducer.receiveSnapshot(a, ReducerTestFixtures.active(session = ReducerTestFixtures.sessionA, revision = 20), 1)

        val staleB = ReducerTestFixtures.id(11)
        val latestB = ReducerTestFixtures.id(12)
        reducer.issueHandshake(staleB, 2)
        reducer.issueHandshake(latestB, 3)
        assertFalse(
            reducer.receiveSnapshot(
                staleB,
                ReducerTestFixtures.active(session = ReducerTestFixtures.sessionB, revision = 0),
                4,
            ).accepted,
        )
        assertEquals(ReducerTestFixtures.sessionA, activePayload(reducer).sessionUuid)
        assertTrue(
            reducer.receiveSnapshot(
                latestB,
                ReducerTestFixtures.active(session = ReducerTestFixtures.sessionB, revision = 0),
                5,
            ).accepted,
        )
        assertEquals(ReducerTestFixtures.sessionB, activePayload(reducer).sessionUuid)
    }

    @Test
    fun `NoSession is a latest-only identity boundary`() {
        val reducer = WatchWorkoutReducer()
        val active = ReducerTestFixtures.id(13)
        reducer.issueHandshake(active, 0)
        reducer.receiveSnapshot(active, ReducerTestFixtures.active(), 1)

        val oldNoSession = ReducerTestFixtures.id(14)
        val latest = ReducerTestFixtures.id(15)
        reducer.issueHandshake(oldNoSession, 2)
        reducer.issueHandshake(latest, 3)
        assertFalse(reducer.receiveSnapshot(oldNoSession, ReducerTestFixtures.noSession(), 4).accepted)
        assertIs<WatchDisplayState.Active>(reducer.state.display)
        assertTrue(reducer.receiveSnapshot(latest, ReducerTestFixtures.noSession(), 5).accepted)
        assertIs<WatchDisplayState.NoSession>(reducer.state.display)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
    }

    @Test
    fun `unsolicited newer same-session data is read-only and different identity only requests refresh`() {
        val reducer = WatchWorkoutReducer()
        val correlation = ReducerTestFixtures.id(16)
        reducer.issueHandshake(correlation, 0)
        reducer.receiveSnapshot(correlation, ReducerTestFixtures.active(revision = 1), 1)

        val sameSession = reducer.receiveUnsolicited(
            ReducerTestFixtures.active(revision = 2, leaseGeneration = 2),
        )
        assertTrue(sameSession.accepted)
        assertEquals(2, activePayload(reducer).sessionRevision)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
        assertTrue(reducer.state.refreshRequired)

        val differentSession = reducer.receiveUnsolicited(
            ReducerTestFixtures.active(session = ReducerTestFixtures.sessionB, revision = 99),
        )
        assertFalse(differentSession.accepted)
        assertEquals(ReducerTestFixtures.sessionA, activePayload(reducer).sessionUuid)
        assertTrue(reducer.state.refreshRequired)
    }

    @Test
    fun `lower-generation unavailable response cannot demote newer admitted lease`() {
        val reducer = WatchWorkoutReducer()
        val older = ReducerTestFixtures.id(17)
        val latest = ReducerTestFixtures.id(18)
        reducer.issueHandshake(older, 0)
        reducer.issueHandshake(latest, 1)
        reducer.receiveSnapshot(
            latest,
            ReducerTestFixtures.active(leaseGeneration = 2, leaseId = ReducerTestFixtures.lease2),
            2,
        )
        assertFalse(
            reducer.receiveSnapshot(older, ReducerTestFixtures.active(unavailable = true), 3).accepted,
        )
        assertEquals(
            2,
            assertIs<LocalMutationAuthority.Available>(reducer.state.authority).leaseGeneration,
        )
    }

    private fun active(reducer: WatchWorkoutReducer): WatchDisplayState.Active =
        assertIs(reducer.state.display)

    private fun activePayload(reducer: WatchWorkoutReducer): SnapshotPayload.ActiveWithTarget =
        assertIs(active(reducer).snapshot.payload)
}
