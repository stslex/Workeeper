// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import kotlin.uuid.Uuid

/** What a watch shows: the active session and its Wear revision. A null key means no session. */
internal data class WatchStateKey(val sessionUuid: CanonicalUuid, val revision: Long) {

    /** GUARD: never names the session; keys are not logged (wear-live-sync.md §6.2). */
    override fun toString(): String = "WatchStateKey"

    companion object {
        fun of(sessionUuid: Uuid, revision: Long): WatchStateKey =
            WatchStateKey(CanonicalUuid.parse(sessionUuid.toString()), revision)
    }
}

/** The key of what a snapshot shows; null when it shows no session. */
internal fun SnapshotData.watchStateKey(): WatchStateKey? =
    payload.sessionIdentityOrNull()?.let { (session, revision) -> WatchStateKey(session, revision) }

/**
 * Per watch node, the key of the last snapshot the bridge answered that node with
 * (wear-live-sync.md §6.2). The bridge writes it; the change notifier reads it and skips a watch that
 * already holds the current key (D7).
 *
 * In process memory only and thread-safe. GUARD: entries are never persisted or logged; at most
 * [MAX_ENTRIES] are kept, the least recently written first out. Public only so `AppGraph` can expose
 * the one app-scoped instance to its identity test (§10.1); every member stays internal.
 */
@SingleIn(AppScope::class)
class WatchKnownRevisions @Inject internal constructor() {

    private val lock = Any()

    /** Insertion-ordered; [record] re-inserts, so the first entry is the least recently written. */
    private val entries = LinkedHashMap<String, Known>()

    /** [key] is null when the answered snapshot showed no session. */
    internal fun record(nodeId: String, key: WatchStateKey?) {
        synchronized(lock) {
            entries.remove(nodeId)
            entries[nodeId] = Known(key)
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
    }

    /**
     * wear-live-sync.md §6.4 change 1: the bridge passes every response it returns, cached or fresh,
     * after any read-only refresh and never a prepared one, so [nodeId] is known to show its snapshot.
     */
    internal fun shown(nodeId: String, response: ActiveWorkoutSnapshotResponse): ActiveWorkoutSnapshotResponse =
        response.also { record(nodeId, it.snapshot.watchStateKey()) }

    /** As above, for a command or protocol-rejection response: [nodeId] shows its replacement. */
    internal fun shown(nodeId: String, response: CompleteCurrentSetResponse): CompleteCurrentSetResponse =
        response.also { record(nodeId, it.replacement.watchStateKey()) }

    /** True only when [nodeId] was last answered with exactly [key]; a node never answered is stale. */
    internal fun holds(nodeId: String, key: WatchStateKey?): Boolean = synchronized(lock) {
        entries[nodeId]?.let { known -> known.key == key } ?: false
    }

    private class Known(val key: WatchStateKey?)

    private companion object {
        const val MAX_ENTRIES = 16
    }
}
