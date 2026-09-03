// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The meta line's token order — when · how long · how much. A golden of one row shows the string
 * that resulted and cannot tell a deliberate order from an accidental one.
 */
internal class RecentMetaLineTest {

    private val item = RecentSessionItem(
        sessionUuid = "s1",
        trainingName = "Ноги",
        isAdhoc = false,
        finishedAtRelativeLabel = "вчера",
        durationLabel = "47:12",
        statsLabel = "5 упражнений · 18 подходов",
    )

    @Test
    @DisplayName("when · how long · how much, joined by the drawn interpunct")
    fun order() {
        assertEquals("вчера · 47:12 · 5 упражнений · 18 подходов", item.metaLine())
    }

    @Test
    @DisplayName("the relative label leads — it is what orders the list being scanned")
    fun relativeLabelIsFirst() {
        // Pinned separately so a reordering reddens something that names the defect.
        assertEquals(true, item.metaLine().startsWith(item.finishedAtRelativeLabel))
    }

    @Test
    @DisplayName("stats trail — the tail is what disappears, and counts are recoverable")
    fun statsAreLast() {
        assertEquals(true, item.metaLine().endsWith(item.statsLabel))
    }

    @Test
    @DisplayName("an empty token is dropped, not left as a dangling separator")
    fun emptyTokensAreDropped() {
        // Reachable: a zero-length token would otherwise print a leading « · ».
        assertEquals(
            "47:12 · 5 упражнений · 18 подходов",
            item.copy(finishedAtRelativeLabel = "").metaLine(),
        )
        assertEquals("вчера", item.copy(durationLabel = "", statsLabel = "").metaLine())
    }
}
