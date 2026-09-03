// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.LoadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Every branch of the tail decision, including the absence (§27). */
internal class PagingTailKindTest {

    @Test
    fun `appending draws the loading footer`() {
        assertEquals(PagingTailKind.LOADING, pagingTailKind(LoadState.Loading))
    }

    @Test
    fun `a failed page draws the error footer, not silence`() {
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
