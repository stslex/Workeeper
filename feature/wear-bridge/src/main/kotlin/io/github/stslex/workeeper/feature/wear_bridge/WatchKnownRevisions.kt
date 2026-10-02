// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
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

/**
 * Per watch node, the key of the last snapshot the bridge answered that node with
 * (wear-live-sync.md §6.2). The bridge writes it; the change notifier reads it and skips a watch that
 * already holds the current key (D7).
 *
 * In process memory only and thread-safe. GUARD: entries are never persisted or logged; at most
 * [MAX_ENTRIES] are kept, the least recently written first out.
 */
@SingleIn(AppScope::class)
internal class WatchKnownRevisions @Inject constructor() {

    private val lock = Any()

    /** Insertion-ordered; [record] re-inserts, so the first entry is the least recently written. */
    private val entries = LinkedHashMap<String, Known>()

    /** [key] is null when the answered snapshot showed no session. */
    fun record(nodeId: String, key: WatchStateKey?) {
        synchronized(lock) {
            entries.remove(nodeId)
            entries[nodeId] = Known(key)
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
    }

    /** True only when [nodeId] was last answered with exactly [key]; a node never answered is stale. */
    fun holds(nodeId: String, key: WatchStateKey?): Boolean = synchronized(lock) {
        entries[nodeId]?.let { known -> known.key == key } ?: false
    }

    private class Known(val key: WatchStateKey?)

    private companion object {
        const val MAX_ENTRIES = 16
    }
}
