// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.AtomicRecordStorage
import io.github.stslex.workeeper.wear.cache.BootCountProvider
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.ongoing.OngoingNotification
import io.github.stslex.workeeper.wear.ongoing.OngoingPolicy
import io.github.stslex.workeeper.wear.ongoing.RecordingNotification
import io.github.stslex.workeeper.wear.ongoing.RecordingStorage
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

internal class RuntimeTestEnvironment(
    transformClock: (ElapsedRealtimeClock) -> ElapsedRealtimeClock = { it },
    decorateNotification: (OngoingNotification) -> OngoingNotification = { it },
) {
    val trace = mutableListOf<String>()
    var now = 1_000L
    var boot: Int? = 1
    val clock = transformClock(ElapsedRealtimeClock { now })
    val storage = RecordingStorage(trace)
    var reads = 0
    var failNextRead = false
    val notification = RecordingNotification(clock, trace)
    val adapter = decorateNotification(notification)
    val scheduler = RecordingDeadlineScheduler()
    var idCounter = 0
    val ids = RuntimeIdSource { ReducerTestFixtures.id(++idCounter) }
    val countedStorage = object : AtomicRecordStorage {
        override fun read(): ByteArray? {
            reads += 1
            if (failNextRead) {
                failNextRead = false
                throw IOException("Transient cache read failure")
            }
            return storage.read()
        }

        override fun replace(bytes: ByteArray) = storage.replace(bytes)
        override fun delete(): Boolean = storage.delete()
    }
    val owner = newOwner()

    fun newOwner(): WatchRuntimeOwner = WatchRuntimeOwner(
        storage = countedStorage,
        clock = clock,
        bootCount = BootCountProvider { boot },
        notification = adapter,
        policy = OngoingPolicy(reconnectWindowMs = 5_000L),
        identity = RuntimeIdentity(sourceNodeId = "synthetic-test-watch", ids = ids),
        scheduler = scheduler,
        selectedLocale = Locale.US,
    )

    fun accept(snapshot: SnapshotData = ReducerTestFixtures.active()): Boolean {
        val request = owner.issueHandshake()
        return owner.receiveSnapshot(
            ActiveWorkoutSnapshotResponse(WearProtocol.SCHEMA_VERSION, request.correlationId, snapshot),
        )
    }

    fun recordPublications(): Job = CoroutineScope(Dispatchers.Unconfined).launch {
        owner.surface.drop(1).collect { trace += "surface:${it.kind}" }
    }
}

internal class RecordingDeadlineScheduler : RuntimeDeadlineScheduler {
    var deadline: Long? = null
    var callback: (() -> Unit)? = null

    override fun replace(deadlineElapsedRealtimeMs: Long?, callback: () -> Unit) {
        deadline = deadlineElapsedRealtimeMs
        this.callback = if (deadlineElapsedRealtimeMs == null) null else callback
    }

    fun fire() = requireNotNull(callback).invoke()
}
