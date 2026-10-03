// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** wear-live-sync.md §6.2: what a watch was last answered with, per node, in bounded memory. */
internal class WatchKnownRevisionsTest {

    private val known = WatchKnownRevisions()
    private val session = CanonicalUuid.parse("cccccccc-0000-4000-8000-000000000002")

    @Test
    fun `a node never answered holds nothing, not even no session`() {
        assertFalse(known.holds(NODE, WatchStateKey(session, 1)))
        assertFalse(known.holds(NODE, null))
    }

    @Test
    fun `a node holds exactly the key it was last answered with`() {
        known.record(NODE, WatchStateKey(session, 1))
        assertTrue(known.holds(NODE, WatchStateKey(session, 1)))
        assertFalse(known.holds(NODE, WatchStateKey(session, 2)))
        assertFalse(known.holds(NODE, null))

        known.record(NODE, null)
        assertTrue(known.holds(NODE, null), "an answer with no session is a known state")
        assertFalse(known.holds(NODE, WatchStateKey(session, 1)))
    }

    @Test
    fun `at most sixteen nodes are kept, the least recently written first out`() {
        repeat(MAX_ENTRIES) { index -> known.record("node-$index", WatchStateKey(session, 1)) }
        known.record("node-0", WatchStateKey(session, 2))

        known.record("node-new", WatchStateKey(session, 1))

        assertFalse(known.holds("node-1", WatchStateKey(session, 1)), "the least recently written left")
        assertTrue(known.holds("node-0", WatchStateKey(session, 2)), "a rewrite made node-0 recent again")
        assertTrue(known.holds("node-new", WatchStateKey(session, 1)))
        assertTrue((2 until MAX_ENTRIES).all { known.holds("node-$it", WatchStateKey(session, 1)) }, "the rest stay")
    }

    @Test
    fun `a key never prints its session`() {
        assertEquals("WatchStateKey", WatchStateKey(session, 7).toString())
    }

    private companion object {
        const val NODE = "watch-node-7f3a"
        const val MAX_ENTRIES = 16
    }
}
