// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The meta line's **token order**, which is a decision and not a transcription.
 *
 * §26 "Meta-line order" fixes the rule — information first, tags last, because the line does not
 * wrap so what truncates is always the tail. It does not fix Home's tokens, because Home is not
 * drawn: `pass2d.html` has eleven sections and none of them is this screen. The order chosen is
 * *when · how long · how much*, following `#s-nav`'s drawn session row («12 июля · 6 упражнений»,
 * when-then-count) with duration inserted between.
 *
 * A golden of one row cannot assert an order — it shows the string that resulted, and a reviewer
 * comparing two pictures cannot tell a deliberate order from an accidental one. Hence a pure
 * function and this file (§27).
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
        // Pinned separately from the full-string case so a reordering that keeps every token still
        // reddens something that names the defect, rather than only a long equality that reads as
        // "the string changed".
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
        // Reachable: `getAbbreviatedRelativeTime` and `formatElapsedDuration` both return strings, and a
        // zero-length one would otherwise print a leading « · ». The filter is one call and the
        // failure it prevents is visible on every row at once.
        assertEquals(
            "47:12 · 5 упражнений · 18 подходов",
            item.copy(finishedAtRelativeLabel = "").metaLine(),
        )
        assertEquals("вчера", item.copy(durationLabel = "", statsLabel = "").metaLine())
    }
}
