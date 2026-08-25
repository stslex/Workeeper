// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.paging.LoadState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The tail wiring, which no whole-screen golden can reach: Paparazzi renders one frame that never
 * appends and never fails. See documentation/feature-specs/all-exercises-delta.md.
 */
internal class PagingTailKindTest {

    @Test
    fun `appending draws the loading footer`() {
        assertEquals(PagingTailKind.LOADING, pagingTailKind(LoadState.Loading))
    }

    @Test
    fun `a failed page draws the error footer, not silence`() {
        assertEquals(
            PagingTailKind.ERROR,
            pagingTailKind(LoadState.Error(IllegalStateException("page failed"))),
        )
    }

    @Test
    fun `exhausted draws no footer at all`() {
        assertEquals(
            PagingTailKind.NONE,
            pagingTailKind(LoadState.NotLoading(endOfPaginationReached = true)),
        )
    }

    @Test
    fun `idle mid-list draws no footer either`() {
        assertEquals(
            PagingTailKind.NONE,
            pagingTailKind(LoadState.NotLoading(endOfPaginationReached = false)),
        )
    }
}
