// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The swap ABOVE the region — archive's second gate, and the one that had no test.
 *
 * `ArchiveListSurfaceTest` gates the verdict and `LoadingVisibilityTest` gates the deferral; this
 * gates the branch between them, which is where both were being discarded. Archive swaps its body
 * (`LazyColumn` **or** region) where the two list screens layer one over the other, so it has a
 * gate the siblings do not, and that gate decides whether the deferral is in composition at all.
 *
 * §27's red-mutation twin is why this file exists rather than a comment: four mutations of the
 * deferral's durations reddened while nothing on this screen consumed the result. A gate names its
 * subject and its consumer, and here the consumer is [archiveBody].
 */
internal class ArchiveBodyTest {

    @Test
    @DisplayName("rows draw the list")
    fun contentDrawsTheList() {
        assertEquals(ArchiveBody.LIST, archiveBody(ArchiveListSurface.CONTENT))
    }

    @Test
    @DisplayName("the HOLD keeps the region on screen after the data says CONTENT")
    fun holdKeepsTheRegion() {
        // The defect this closes, stated as its input: during the minimum hold the deferred
        // verdict is LOADING while `archiveListSurface` has already returned CONTENT. A swap that
        // re-read the raw verdict here would drop the region — and with it the composable holding
        // the spinner — at the exact millisecond the hold began, which is the flash the two
        // numbers exist to remove.
        assertEquals(ArchiveBody.REGION, archiveBody(ArchiveListSurface.LOADING))
    }

    @Test
    @DisplayName("the deferral window is the REGION, drawing nothing — never an empty list")
    fun deferralWindowIsTheRegion() {
        // `null` means "loading, nothing shown yet". The region renders nothing for it, so the
        // outgoing frame persists. Routing it to LIST instead would draw an empty `LazyColumn`
        // for the first 140 ms of every cold open — B22's blank frame by a new road, and the
        // reason this case is asserted rather than left to `!= CONTENT` reading obviously right.
        assertEquals(ArchiveBody.REGION, archiveBody(null))
    }

    @Test
    @DisplayName("every settled non-content verdict is the region")
    fun settledEmptyStatesAreTheRegion() {
        assertEquals(ArchiveBody.REGION, archiveBody(ArchiveListSurface.EMPTY))
        assertEquals(ArchiveBody.REGION, archiveBody(ArchiveListSurface.REFRESH_ERROR))
    }
}
