// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.LoadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Which tail is drawn — the half a golden cannot reach.
 *
 * The footers themselves are photographed by `HomeGoldenTest`. What no picture can see is *when*
 * they appear: Paparazzi renders one frame of a `PagingData.from` source, which never appends and
 * never fails, so the `LoadState.Error` branch is invisible to the visual gate on every screen that
 * has one. Measured on `all-exercises`, where deleting that branch left 30 goldens byte-identical.
 *
 * This is the module's own copy rather than a shared test, per §27's MATCH rule: a behavioural
 * parity claim either cites a test covering **both** sides or is marked unverified, and one test
 * plus an assertion of sameness is the shape that let `all-exercises` ship a doubled haptic.
 */
internal class HomePagingTailKindTest {

    @Test
    @DisplayName("an appending list draws the spinner footer")
    fun appending() {
        assertEquals(PagingTailKind.LOADING, pagingTailKind(LoadState.Loading))
    }

    @Test
    @DisplayName("a failed append draws the error footer — the branch goldens cannot reach")
    fun failedAppend() {
        assertEquals(
            PagingTailKind.ERROR,
            pagingTailKind(LoadState.Error(IllegalStateException("boom"))),
        )
    }

    @Test
    @DisplayName("an exhausted list draws NOTHING, and the absence is the assertion")
    fun exhausted() {
        // §26 "Paging tails": three states, two drawings — "end of list" states nothing beyond what
        // is already visible. NONE is what a missing branch produces by accident too, so it is the
        // case where a green test proves least unless it is written on purpose.
        assertEquals(PagingTailKind.NONE, pagingTailKind(LoadState.NotLoading(endOfPaginationReached = true)))
        assertEquals(PagingTailKind.NONE, pagingTailKind(LoadState.NotLoading(endOfPaginationReached = false)))
    }
}
