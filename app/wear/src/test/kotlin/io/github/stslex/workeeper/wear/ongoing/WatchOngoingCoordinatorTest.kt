// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.CacheReadResult
import io.github.stslex.workeeper.wear.cache.CacheRecord
import io.github.stslex.workeeper.wear.cache.CachedConnection
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.SnapshotReduction
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class WatchOngoingCoordinatorTest {
    @Test
    fun `fresh admitted active snapshot persists its deadline before posting`() {
        val env = OngoingTestEnvironment()
        val status = assertIs<OngoingStatus.Scheduled>(env.accept())

        assertEquals(126_000L, status.stopAtElapsedRealtimeMs)
        assertEquals(listOf("cache:126000", "post:125000"), env.events)
        assertEquals(126_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertEquals(1_000L, env.cachedRecord().receivedAtElapsedRealtimeMs)
        assertEquals(125_000L, env.notification.posts.single().timeoutAfterMs)
    }

    @Test
    fun `time spent publishing cache is deducted before the system timeout is set`() {
        val env = OngoingTestEnvironment()
        env.storage.afterReplace = { env.now += 500 }
        env.accept()

        assertEquals(124_500L, env.notification.posts.single().timeoutAfterMs)
        assertEquals(126_000L, env.notification.deadline)
    }

    @Test
    fun `rejected reduction cannot start or replace a lifecycle`() {
        val env = OngoingTestEnvironment()
        val incoming = env.admission()
        env.publish(incoming.copy(reduction = SnapshotReduction(false, null)))
        assertEquals(OngoingStatus.Inactive, env.coordinator.status())
        assertTrue(env.events.isEmpty())

        env.publish(incoming)
        val bytes = env.storage.bytes?.copyOf()
        env.now = 2_000
        env.events.clear()
        env.publish(incoming.copy(reduction = SnapshotReduction(false, null)))
        assertTrue(bytes.contentEquals(env.storage.bytes))
        assertTrue(env.events.isEmpty())
        assertEquals(1, env.notification.posts.size)
    }

    @Test
    fun `wire grant without fresh admitted authority never starts`() {
        val env = OngoingTestEnvironment()
        val incoming = env.admission()
        env.publish(incoming.copy(state = incoming.state.copy(authority = LocalMutationAuthority.Retired)))

        assertNull(env.cachedRecord().effectiveMutationWindowMs)
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertTrue(env.notification.posts.isEmpty())
    }

    @Test
    fun `unavailable and already expired snapshots persist without ongoing deadline`() {
        val env = OngoingTestEnvironment()
        env.accept(ReducerTestFixtures.active(unavailable = true))
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertNull(env.cachedRecord().effectiveMutationWindowMs)

        val incoming = env.admission(ReducerTestFixtures.active(leaseGeneration = 2))
        env.now += WearProtocol.MAX_MUTATION_WINDOW_MS
        env.publish(incoming)
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertTrue(env.notification.posts.isEmpty())
    }

    @Test
    fun `disconnect shortens notification before atomically changing its cache header`() {
        val env = OngoingTestEnvironment()
        env.accept()
        val payload = env.cachedRecord().payload
        env.now = 2_000
        env.events.clear()
        env.coordinator.disconnected(env.now)

        assertEquals(listOf("update:5000", "cache:7000"), env.events)
        val record = env.cachedRecord()
        assertEquals(7_000L, record.ongoingStopAtElapsedRealtimeMs)
        assertEquals(CachedConnection.DISCONNECTED, record.connection)
        assertEquals(1_000L, record.receivedAtElapsedRealtimeMs)
        assertEquals(WearProtocol.MAX_MUTATION_WINDOW_MS, record.effectiveMutationWindowMs)
        assertTrue(payload.contentEquals(record.payload))

        assertDisconnectClearsRetention { notification.allowed = false }
        assertDisconnectClearsRetention { notification.denyNextUpdate = true }
        assertDisconnectClearsRetention { notification.removeBeforeNextUpdate = true }
        assertDisconnectClearsRetention { now = 126_000 }
    }

    @Test
    fun `repeated disconnect refresh and connected silence never extend the deadline`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.coordinator.disconnected(env.now)
        env.now = 3_000
        env.coordinator.disconnected(env.now)
        env.now = 4_000
        env.coordinator.refresh()

        assertEquals(listOf(5_000L, 4_000L, 3_000L), env.notification.updates.map { it.timeoutAfterMs })
        assertEquals(7_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertEquals(7_000L, env.notification.deadline)
    }

    @Test
    fun `disconnect predating a fresh snapshot cannot shorten the new lifecycle`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.accept(ReducerTestFixtures.active(leaseGeneration = 2))
        env.events.clear()
        env.coordinator.disconnected(1_500)

        assertTrue(env.events.isEmpty())
        assertEquals(127_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)

        val denied = OngoingTestEnvironment()
        denied.notification.allowed = false
        denied.accept()
        denied.now = 2_000
        denied.accept(ReducerTestFixtures.active(leaseGeneration = 2))
        val connected = denied.cachedRecord()
        denied.events.clear()
        denied.coordinator.disconnected(1_500)
        assertEquals(connected, denied.cachedRecord())
        assertTrue(denied.events.isEmpty())

        val restarted = denied.newCoordinator()
        restarted.restore()
        denied.events.clear()
        restarted.disconnected(1_500)
        assertEquals(connected, denied.cachedRecord())
        assertTrue(denied.events.isEmpty())
        restarted.disconnected(denied.now)
        assertDisconnectedCache(denied, connected)
    }

    @Test
    fun `fresh correlated successor can start a new lifecycle after grace`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.coordinator.disconnected(env.now)
        env.now = 3_000
        env.accept(ReducerTestFixtures.active(leaseGeneration = 2))

        assertEquals(128_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertEquals(128_000L, env.notification.deadline)
        assertEquals(2, env.notification.posts.size)
    }

    @Test
    fun `shorter fresh grant bounds the previous notification before cache publication`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.events.clear()
        var deadlineAtCachePublication: Long? = null
        env.storage.afterReplace = { deadlineAtCachePublication = env.notification.deadline }

        val status = env.accept(ReducerTestFixtures.active(leaseGeneration = 2, remainingMs = 1_000))

        assertEquals(OngoingStatus.Scheduled(8_000L), status)
        assertEquals(8_000L, deadlineAtCachePublication)
        assertEquals(listOf("update:6000", "cache:8000", "post:6000"), env.events)
        assertEquals(8_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
    }

    @Test
    fun `shorter fresh grant stays bounded through either cache publication crash cut`() {
        WriteFailure.entries.forEach { failure ->
            val env = OngoingTestEnvironment()
            env.accept()
            env.now = 2_000
            env.storage.failure = failure

            assertFailsWith<IOException> {
                env.accept(ReducerTestFixtures.active(leaseGeneration = 2, remainingMs = 1_000))
            }

            assertEquals(8_000L, env.notification.deadline, failure.name)
            assertEquals(1, env.notification.posts.size, "no fresh post before successful durable publication")
            env.storage.failure = null
            env.now = 3_000
            val restarted = env.newCoordinator()
            val restored = assertIs<CacheReadResult.DisplayOnly>(restarted.restore())
            assertEquals(8_000L, restored.ongoingStopAtElapsedRealtimeMs, failure.name)
            restarted.refresh()
            assertEquals(8_000L, env.notification.deadline, failure.name)
            assertEquals(5_000L, env.notification.updates.last().timeoutAfterMs, failure.name)
        }
    }

    @Test
    fun `shorter fresh grant preflight denial clears retention without posting`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.notification.denyNextUpdate = true
        env.events.clear()

        val status = env.accept(ReducerTestFixtures.active(leaseGeneration = 2, remainingMs = 1_000))

        assertEquals(OngoingStatus.PermissionDenied, status)
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertEquals(1_000L, env.cachedRecord().effectiveMutationWindowMs)
        assertEquals(1, env.notification.posts.size)
        assertNull(env.notification.deadline)
        assertEquals(listOf("update:6000", "cancel", "cache:null"), env.events)
    }

    @Test
    fun `shorter fresh grant cannot recreate a notification removed during preflight`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.notification.removeBeforeNextUpdate = true
        env.events.clear()

        val status = env.accept(ReducerTestFixtures.active(leaseGeneration = 2, remainingMs = 1_000))

        assertEquals(OngoingStatus.Inactive, status)
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertEquals(1, env.notification.posts.size)
        assertNull(env.notification.deadline)
        assertEquals(listOf("update:6000", "cancel", "cache:null"), env.events)
    }

    @Test
    fun `freshness expiry retains only the original bounded grace interval`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 121_000
        env.coordinator.refresh()
        assertEquals(5_000L, env.notification.updates.single().timeoutAfterMs)
        env.now = 125_999
        env.coordinator.refresh()
        assertEquals(1L, env.notification.updates.last().timeoutAfterMs)
        env.now = 126_000
        env.coordinator.refresh()
        assertEquals(OngoingStatus.Inactive, env.coordinator.status())
        assertNull(env.notification.deadline)
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)

        val cached = env.cachedRecord()
        env.events.clear()
        env.coordinator.disconnected(env.now)
        assertDisconnectedCache(env, cached)
        assertEquals(listOf("cache:null"), env.events)
        assertEquals(1, env.notification.posts.size)
    }

    @Test
    fun `system timeout expires without a coordinator tick`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.events.clear()
        env.now = 126_000

        assertNull(env.notification.existingDeadlineMs())
        assertEquals(OngoingStatus.Inactive, env.coordinator.status())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun `terminal no-session cancels before publishing a payload-free tombstone`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 2_000
        env.events.clear()
        env.accept(ReducerTestFixtures.noSession())

        assertEquals(listOf("cancel", "cache:null"), env.events)
        assertTrue(env.cachedRecord().isNoSessionTombstone)
        assertEquals(OngoingStatus.Inactive, env.coordinator.status())
    }

    @Test
    fun `all non-active display states immediately cancel the existing notification`() {
        val snapshot = ReducerTestFixtures.active()
        val terminals = listOf(
            WatchDisplayState.Loading,
            WatchDisplayState.NoSession(ReducerTestFixtures.epoch),
            WatchDisplayState.PhoneActionRequired(snapshot),
            WatchDisplayState.WorkoutComplete(snapshot),
            WatchDisplayState.ProtocolMismatch(reason = null),
        )
        terminals.forEach { terminal ->
            val env = OngoingTestEnvironment()
            env.accept()
            env.events.clear()
            env.coordinator.displayChanged(terminal)
            assertEquals(listOf("cancel", "cache:null"), env.events)
            assertEquals(OngoingStatus.Inactive, env.coordinator.status())
        }
    }

    @Test
    fun `permission denial preserves ordinary cache but does not claim ongoing retention`() {
        val env = OngoingTestEnvironment()
        env.notification.allowed = false
        assertEquals(OngoingStatus.PermissionDenied, env.accept())
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        assertEquals(WearProtocol.MAX_MUTATION_WINDOW_MS, env.cachedRecord().effectiveMutationWindowMs)
        assertTrue(env.notification.posts.isEmpty())
        assertIs<CacheReadResult.DisplayOnly>(env.cache.read())

        val cached = env.cachedRecord()
        env.now = 2_000
        env.events.clear()
        assertFailsWith<IllegalArgumentException> { env.coordinator.disconnected(env.now + 1) }
        assertEquals(cached, env.cachedRecord())
        assertTrue(env.events.isEmpty())
        assertEquals(OngoingStatus.PermissionDenied, env.coordinator.disconnected(env.now))
        assertDisconnectedCache(env, cached)
        assertEquals(listOf("cache:null"), env.events)
        assertTrue(env.notification.posts.isEmpty())
        val restored = assertIs<CacheReadResult.DisplayOnly>(env.newCoordinator().restore())
        assertEquals(CachedConnection.DISCONNECTED, restored.connection)
        val target = assertIs<SnapshotPayload.ActiveWithTarget>(restored.snapshot.payload)
        assertIs<MutationAuthority.Unavailable>(target.mutationAuthority)

        val absent = OngoingTestEnvironment()
        assertFailsWith<IllegalArgumentException> { absent.coordinator.disconnected(absent.now + 1) }
        assertEquals(OngoingStatus.Inactive, absent.coordinator.disconnected(absent.now))
        assertNull(absent.storage.bytes)
        assertTrue(absent.events.isEmpty())
    }

    @Test
    fun `restored permission clears denial status without posting or renewing`() {
        val env = OngoingTestEnvironment()
        env.notification.allowed = false
        assertEquals(OngoingStatus.PermissionDenied, env.accept())
        env.events.clear()
        env.notification.allowed = true

        assertEquals(OngoingStatus.Inactive, env.coordinator.status())
        assertEquals(OngoingStatus.Inactive, env.coordinator.refresh())
        assertTrue(env.events.isEmpty())
        assertTrue(env.notification.posts.isEmpty())
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
    }

    @Test
    fun `permission revoked during posting clears the already-persisted deadline`() {
        val env = OngoingTestEnvironment()
        env.notification.denyNextPost = true
        assertEquals(OngoingStatus.PermissionDenied, env.accept())

        assertEquals(listOf("cache:126000", "post:125000", "cancel", "cache:null"), env.events)
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
        env.notification.allowed = true
        env.coordinator.refresh()
        assertTrue(env.notification.posts.isEmpty())
    }

    @Test
    fun `restart restores read-only cache and never creates a missing notification`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.notification.deadline = null
        env.events.clear()
        val restarted = env.newCoordinator()
        val restored = assertIs<CacheReadResult.DisplayOnly>(restarted.restore())
        val active = assertIs<SnapshotPayload.ActiveWithTarget>(restored.snapshot.payload)

        assertIs<MutationAuthority.Unavailable>(active.mutationAuthority)
        assertEquals(1, env.notification.posts.size)
        assertEquals(OngoingStatus.Inactive, restarted.status())
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
    }

    @Test
    fun `surviving notification refresh after restart uses only remaining interval`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.now = 3_000
        val restarted = env.newCoordinator()
        env.events.clear()
        restarted.restore()
        assertTrue(env.events.isEmpty())
        restarted.refresh()

        assertEquals(listOf("update:123000"), env.events)
        assertEquals(1, env.notification.posts.size)
        assertEquals(126_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
    }

    @Test
    fun `missing notification during update is never recreated`() {
        val env = OngoingTestEnvironment()
        env.accept()
        env.notification.deadline = null
        env.coordinator.refresh()

        assertEquals(1, env.notification.posts.size)
        assertEquals(OngoingStatus.Inactive, env.coordinator.status())
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)

        val cached = env.cachedRecord()
        env.now = 2_000
        env.events.clear()
        env.coordinator.disconnected(env.now)
        assertDisconnectedCache(env, cached)
        assertEquals(listOf("cache:null"), env.events)
        assertEquals(1, env.notification.posts.size)
    }

    @Test
    fun `failed fresh cache publication cannot expose a new notification`() {
        WriteFailure.entries.forEach { failure ->
            val env = OngoingTestEnvironment()
            env.storage.failure = failure
            assertFailsWith<IOException> { env.accept() }
            assertTrue(env.notification.posts.isEmpty())
            assertNull(env.notification.deadline)
        }
    }

    @Test
    fun `failed shortened cache publication cannot extend the surviving system timeout on restart`() {
        WriteFailure.entries.forEach { failure ->
            val env = OngoingTestEnvironment()
            env.accept()
            env.now = 2_000
            env.storage.failure = failure
            assertFailsWith<IOException> { env.coordinator.disconnected(env.now) }
            assertEquals(7_000L, env.notification.deadline)

            env.storage.failure = null
            env.now = 3_000
            val restarted = env.newCoordinator()
            val restored = assertIs<CacheReadResult.DisplayOnly>(restarted.restore())
            assertEquals(7_000L, restored.ongoingStopAtElapsedRealtimeMs)
            restarted.refresh()
            assertEquals(7_000L, env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
            assertEquals(7_000L, env.notification.deadline)
            assertEquals(4_000L, env.notification.updates.last().timeoutAfterMs)
        }
    }

    @Test
    fun `cache header API refuses extension or installation from a display-only record`() {
        val env = OngoingTestEnvironment()
        env.accept()
        assertFailsWith<IllegalArgumentException> { env.cache.shortenOngoingDeadline(126_001) }
        env.cache.shortenOngoingDeadline(null)
        assertFailsWith<IllegalArgumentException> { env.cache.shortenOngoingDeadline(126_000) }
    }

    @Test
    fun `stale display with a granted wire payload cannot renew ongoing`() {
        val env = OngoingTestEnvironment()
        val incoming = env.admission()
        val display = assertIs<WatchDisplayState.Active>(incoming.state.display)
        val staleState = incoming.state.copy(display = display.copy(freshness = ActiveFreshness.STALE))
        env.publish(incoming.copy(state = staleState))

        assertTrue(env.notification.posts.isEmpty())
        assertNull(env.cachedRecord().ongoingStopAtElapsedRealtimeMs)
    }

    private fun assertDisconnectClearsRetention(prepare: OngoingTestEnvironment.() -> Unit) {
        val env = OngoingTestEnvironment()
        env.accept()
        val cached = env.cachedRecord()
        env.now = 2_000
        env.prepare()
        env.events.clear()
        env.coordinator.disconnected(env.now)

        assertDisconnectedCache(env, cached)
        assertNull(env.notification.deadline)
        assertEquals(1, env.notification.posts.size)
        assertEquals(listOf("cancel", "cache:null"), env.events.takeLast(2))
    }

    private fun assertDisconnectedCache(env: OngoingTestEnvironment, before: CacheRecord) {
        assertEquals(
            before.copy(connection = CachedConnection.DISCONNECTED, ongoingStopAtElapsedRealtimeMs = null),
            env.cachedRecord(),
        )
    }
}
