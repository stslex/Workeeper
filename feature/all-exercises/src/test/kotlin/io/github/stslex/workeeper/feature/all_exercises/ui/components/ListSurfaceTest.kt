// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The selector, which no golden can see.
 *
 * Paparazzi renders one settled frame of a `PagingData.from` source: it never appends, never
 * fails, and never leaves refresh unsettled — so a whole-screen golden cannot enter [LOADING],
 * [REFRESH_ERROR] or (without a store fixture) [SELECTION_EMPTY]. The screen-level "empty" golden
 * was a picture of B22 for exactly that reason. §27's class, and its remedy: assert the value.
 *
 * Every verdict is covered, **including the ones that render nothing**. [CONTENT] and
 * [REFRESH_ERROR] draw no empty-region treatment today, and "draws nothing" is the outcome a
 * missing branch also produces — so they are the cases where a green test proves least unless it
 * is written deliberately.
 */
internal class ListSurfaceTest {

    private fun states(
        refresh: LoadState = LoadState.NotLoading(false),
        append: LoadState = LoadState.NotLoading(false),
    ) = CombinedLoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(false),
        append = append,
        source = LoadStates(refresh, LoadState.NotLoading(false), append),
    )

    private fun surface(
        itemCount: Int = 0,
        refresh: LoadState = LoadState.NotLoading(false),
        filterActive: Boolean = false,
        selecting: Boolean = false,
    ) = listSurface(itemCount, states(refresh), filterActive, selecting)

    @Test
    fun `rows win over everything`() {
        assertEquals(ListSurface.CONTENT, surface(itemCount = 3))
        assertEquals(
            ListSurface.CONTENT,
            surface(itemCount = 3, refresh = LoadState.Loading, filterActive = true, selecting = true),
        )
    }

    /**
     * B22's fix. Before it, an unsettled refresh with no rows drew **nothing at all** — the list
     * had no rows and the empty state was suppressed by the same predicate.
     */
    @Test
    fun `an unsettled refresh with no rows is loading, not empty`() {
        assertEquals(ListSurface.LOADING, surface(refresh = LoadState.Loading))
    }

    /** Loading outranks the mode and the filter: an unknown list cannot be described. */
    @Test
    fun `loading outranks selection and the filter`() {
        assertEquals(
            ListSurface.LOADING,
            surface(refresh = LoadState.Loading, filterActive = true, selecting = true),
        )
    }

    /** B22's fourth region: a failed FIRST page is not a failed append, and is still undrawn. */
    @Test
    fun `a failed first page is its own verdict`() {
        assertEquals(
            ListSurface.REFRESH_ERROR,
            surface(refresh = LoadState.Error(IllegalStateException("boom"))),
        )
        assertEquals(
            ListSurface.REFRESH_ERROR,
            surface(refresh = LoadState.Error(IllegalStateException("boom")), filterActive = true),
        )
    }

    @Test
    fun `no rows, nothing done, is the first-run empty`() {
        assertEquals(ListSurface.FIRST_RUN, surface())
    }

    @Test
    fun `a filter that matches nothing is its own state, not the first-run empty`() {
        assertEquals(ListSurface.FILTERED_EMPTY, surface(filterActive = true))
    }

    @Test
    fun `selection outranks the filter, because the selection block carries the filter recovery`() {
        assertEquals(ListSurface.SELECTION_EMPTY, surface(selecting = true))
        assertEquals(
            ListSurface.SELECTION_EMPTY,
            surface(filterActive = true, selecting = true),
        )
    }
    /**
     * §26 continuity motion — the empty region's crossfade key.
     *
     * `crossfades` decides whether a verdict change is a transit or a cut, and **no golden can see
     * that decision without being wrong**: the first cut returned `true` for every verdict, and the
     * way that surfaced was ten whole-screen goldens photographing a mid-transition frame instead of
     * their own named state. So the property is asserted here, directly, on both halves.
     *
     * [ListSurface.LOADING] is the load-bearing `false`. Every screen composes it first —
     * `collectAsLazyPagingItems()` begins empty with `refresh = Loading` — so a key spanning it puts
     * an `AnimatedContent` transition inside the one frame Paparazzi renders.
     */
    @Test
    fun `the crossfade covers the drawn blocks and neither non-block verdict`() {
        assertEquals(false, ListSurface.CONTENT.crossfades)
        assertEquals(false, ListSurface.LOADING.crossfades)

        assertEquals(true, ListSurface.REFRESH_ERROR.crossfades)
        assertEquals(true, ListSurface.FIRST_RUN.crossfades)
        assertEquals(true, ListSurface.FILTERED_EMPTY.crossfades)
        assertEquals(true, ListSurface.SELECTION_EMPTY.crossfades)
    }

    /** The pair §26's row actually named: one block replaces the other on the user's gesture. */
    @Test
    fun `selection empty and filtered empty are both in the crossfade, so the pair transits`() {
        assertEquals(true, ListSurface.SELECTION_EMPTY.crossfades)
        assertEquals(true, ListSurface.FILTERED_EMPTY.crossfades)
    }
}
