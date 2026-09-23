// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.AtomicRecordStorage
import io.github.stslex.workeeper.wear.cache.BootCountProvider
import io.github.stslex.workeeper.wear.cache.CacheFraming
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.cache.OngoingExpiryHandler
import io.github.stslex.workeeper.wear.cache.WatchSnapshotCache
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.SnapshotReduction
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OngoingTestEnvironment {
    val events = mutableListOf<String>()
    var now = 1_000L
    val clock = ElapsedRealtimeClock { now }
    val storage = RecordingStorage(events)
    val notification = RecordingNotification(clock, events)
    val cache = newCache()
    val coordinator = newCoordinator(cache)
    private val reducer = WatchWorkoutReducer()
    private var correlation = 0

    fun newCache() = WatchSnapshotCache(
        storage,
        clock,
        BootCountProvider { 1 },
        OngoingExpiryHandler(notification::cancel),
    )

    fun newCoordinator(cache: WatchSnapshotCache = newCache()) = WatchOngoingCoordinator(
        cache,
        clock,
        notification,
        OngoingPolicy(reconnectWindowMs = TEST_RECONNECT_WINDOW_MS),
    )

    fun admission(snapshot: SnapshotData = ReducerTestFixtures.active()): TestAdmission {
        correlation += 1
        val id = ReducerTestFixtures.id(correlation)
        reducer.issueHandshake(id, now)
        val reduction = reducer.receiveSnapshot(id, snapshot, now)
        return TestAdmission(
            ActiveWorkoutSnapshotResponse(WearProtocol.SCHEMA_VERSION, id, snapshot),
            reduction,
            reducer.state,
            now,
        )
    }

    fun accept(snapshot: SnapshotData = ReducerTestFixtures.active()): OngoingStatus = publish(admission(snapshot))

    fun publish(admission: TestAdmission): OngoingStatus = coordinator.acceptSnapshot(
        admission.response,
        admission.reduction,
        admission.state,
        admission.receivedAtMs,
        1,
    )

    fun cachedRecord() = requireNotNull(storage.bytes?.let(CacheFraming::decode))

    companion object {
        // Synthetic test interval only; production values require the physical-watch probe.
        const val TEST_RECONNECT_WINDOW_MS = 5_000L
    }
}

internal data class TestAdmission(
    val response: ActiveWorkoutSnapshotResponse,
    val reduction: SnapshotReduction,
    val state: WatchReducerState,
    val receivedAtMs: Long,
)

internal class RecordingStorage(private val events: MutableList<String>) : AtomicRecordStorage {
    var bytes: ByteArray? = null
    var failure: WriteFailure? = null
    var afterReplace: (() -> Unit)? = null

    override fun read(): ByteArray? = bytes?.copyOf()

    override fun replace(bytes: ByteArray) {
        val record = requireNotNull(CacheFraming.decode(bytes))
        events += "cache:${record.ongoingStopAtElapsedRealtimeMs}"
        if (failure == WriteFailure.BEFORE_PUBLISH) throw IOException("before atomic publication")
        this.bytes = bytes.copyOf()
        if (failure == WriteFailure.AFTER_PUBLISH) throw IOException("after atomic publication")
        afterReplace?.invoke()
    }

    override fun delete(): Boolean {
        bytes = null
        return true
    }
}

internal enum class WriteFailure { BEFORE_PUBLISH, AFTER_PUBLISH }

internal class RecordingNotification(
    private val clock: ElapsedRealtimeClock,
    private val events: MutableList<String>,
) : OngoingNotification {
    var allowed = true
    var denyNextPost = false
    var denyNextUpdate = false
    var removeBeforeNextUpdate = false
    var deadline: Long? = null
    val posts = mutableListOf<OngoingNotificationRequest>()
    val updates = mutableListOf<OngoingNotificationRequest>()

    override fun permissionGranted(): Boolean = allowed

    override fun existingDeadlineMs(): Long? = deadline?.takeIf { clock.nowMs() < it }

    override fun post(request: OngoingNotificationRequest): OngoingPostResult {
        events += "post:${request.timeoutAfterMs}"
        if (!allowed || denyNextPost) {
            denyNextPost = false
            allowed = false
            return OngoingPostResult.PermissionDenied
        }
        assertTrue(request.timeoutAfterMs > 0L, "Posted notification timeout must be positive")
        assertEquals(
            request.stopAtElapsedRealtimeMs,
            clock.nowMs() + request.timeoutAfterMs,
            "Posted timeout must end at the requested absolute deadline",
        )
        posts += request
        deadline = clock.nowMs() + request.timeoutAfterMs
        return OngoingPostResult.Posted(request.stopAtElapsedRealtimeMs)
    }

    override fun updateIfPresent(request: OngoingNotificationRequest): OngoingPostResult {
        events += "update:${request.timeoutAfterMs}"
        if (!allowed || denyNextUpdate) {
            denyNextUpdate = false
            allowed = false
            return OngoingPostResult.PermissionDenied
        }
        if (removeBeforeNextUpdate) {
            removeBeforeNextUpdate = false
            deadline = null
        }
        val existing = existingDeadlineMs() ?: return OngoingPostResult.Missing
        assertTrue(request.timeoutAfterMs > 0L, "Updated notification timeout must be positive")
        assertEquals(
            request.stopAtElapsedRealtimeMs,
            clock.nowMs() + request.timeoutAfterMs,
            "Updated timeout must end at the requested absolute deadline",
        )
        val effective = minOf(existing, request.stopAtElapsedRealtimeMs)
        updates += request.copy(
            stopAtElapsedRealtimeMs = effective,
            timeoutAfterMs = effective - clock.nowMs(),
        )
        deadline = effective
        return OngoingPostResult.Posted(effective)
    }

    override fun cancel() {
        events += "cancel"
        deadline = null
    }
}
