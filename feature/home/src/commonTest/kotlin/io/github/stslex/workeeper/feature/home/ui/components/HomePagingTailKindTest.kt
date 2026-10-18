// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which tail is drawn — the half a golden cannot reach, since Paparazzi renders one frame of a
 * source that never appends and never fails. Each module carries its own copy of this test.
 */
internal class HomePagingTailKindTest {

    /** an appending list draws the spinner footer */
    @Test
    fun appending() {
        assertEquals(PagingTailKind.LOADING, pagingTailKind(LoadState.Loading))
    }

    /** a failed append draws the error footer — the branch goldens cannot reach */
    @Test
    fun failedAppend() {
        assertEquals(
            PagingTailKind.ERROR,
            pagingTailKind(LoadState.Error(IllegalStateException("boom"))),
        )
    }

    /** an exhausted list draws NOTHING, and the absence is the assertion */
    @Test
    fun exhausted() {
        // NONE is also what a missing branch produces, so the absence is asserted on purpose.
        assertEquals(PagingTailKind.NONE, pagingTailKind(LoadState.NotLoading(endOfPaginationReached = true)))
        assertEquals(PagingTailKind.NONE, pagingTailKind(LoadState.NotLoading(endOfPaginationReached = false)))
    }
}
