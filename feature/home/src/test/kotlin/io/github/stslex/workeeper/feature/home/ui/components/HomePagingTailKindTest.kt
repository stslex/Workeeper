// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.LoadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Which tail is drawn — the half a golden cannot reach, since Paparazzi renders one frame of a
 * source that never appends and never fails. Each module carries its own copy of this test.
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
        // NONE is also what a missing branch produces, so the absence is asserted on purpose.
        assertEquals(PagingTailKind.NONE, pagingTailKind(LoadState.NotLoading(endOfPaginationReached = true)))
        assertEquals(PagingTailKind.NONE, pagingTailKind(LoadState.NotLoading(endOfPaginationReached = false)))
    }
}
