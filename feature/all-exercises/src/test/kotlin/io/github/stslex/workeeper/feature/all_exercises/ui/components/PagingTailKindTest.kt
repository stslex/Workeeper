// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.paging.LoadState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The tail wiring, which no golden can see.
 *
 * §26 "Paging tails": three states, **two drawings**. The two footers are photographed; *when* they
 * appear was not, and the hole was measured rather than suspected — deleting the `LoadState.Error`
 * branch from the screen's `LazyListScope` block left all 30 goldens byte-identical. A whole-screen
 * golden cannot reach an append-error state: Paparazzi renders one frame of a `PagingData.from`
 * source, which never appends and never fails.
 *
 * So the decision was extracted to [pagingTailKind] and asserted here. §27's class: name the thing
 * the picture cannot contain, and assert the value itself.
 *
 * [PagingTailKind.NONE] carries **both** exhausted and idle, and that is the drawing — "конец
 * списка" states nothing beyond what is already visible, so the absence *is* the treatment. It is
 * asserted, not assumed, because "no footer" is the one outcome a missing branch produces by
 * accident.
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
