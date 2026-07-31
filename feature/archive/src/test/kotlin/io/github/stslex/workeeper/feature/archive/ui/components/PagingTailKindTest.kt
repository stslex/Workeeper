// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.LoadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The tail decision, every branch — **including the absence**, which is the outcome a missing
 * branch produces by accident and therefore the one a green test proves least about unless it was
 * written deliberately (§27).
 *
 * Third copy of this file, and §27's MATCH rule is why it is a copy rather than a shared assertion:
 * a behavioural parity claim across screens either cites a test covering both sides or is marked
 * unverified. Three screens, three tests.
 */
internal class PagingTailKindTest {

    @Test
    fun `appending draws the loading footer`() {
        assertEquals(PagingTailKind.LOADING, pagingTailKind(LoadState.Loading))
    }

    @Test
    fun `a failed page draws the error footer, not silence`() {
        // The whole point of the tail on this screen: before it existed, a failed append was a list
        // that quietly stopped, which is indistinguishable from a list that finished.
        assertEquals(PagingTailKind.ERROR, pagingTailKind(LoadState.Error(RuntimeException())))
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
