// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The selector, which no golden can see. Every verdict is covered, [HomeListSurface.CONTENT]
 * included — "draws nothing" is also what a missing branch produces.
 */
internal class HomeListSurfaceTest {

    private fun states(refresh: LoadState) = CombinedLoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(false),
        append = LoadState.NotLoading(false),
        source = LoadStates(refresh, LoadState.NotLoading(false), LoadState.NotLoading(false)),
    )

    private fun surface(itemCount: Int = 0, refresh: LoadState = LoadState.NotLoading(false)) =
        homeListSurface(itemCount, states(refresh))

    /** rows win over everything, including an unsettled refresh */
    @Test
    fun rowsWin() {
        assertEquals(HomeListSurface.CONTENT, surface(itemCount = 2))
        assertEquals(HomeListSurface.CONTENT, surface(itemCount = 2, refresh = LoadState.Loading))
    }

    /** an unsettled refresh with no rows is loading, not empty — B22 */
    @Test
    fun coldOpenIsLoading() {
        assertEquals(HomeListSurface.LOADING, surface(refresh = LoadState.Loading))
    }

    /** a failed first page is its own verdict, not loading and not empty */
    @Test
    fun coldOpenErrorIsItsOwnVerdict() {
        assertEquals(
            HomeListSurface.REFRESH_ERROR,
            surface(refresh = LoadState.Error(IllegalStateException("boom"))),
        )
    }

    /** settled with no rows is the empty state */
    @Test
    fun settledAndEmpty() {
        assertEquals(HomeListSurface.EMPTY, surface())
    }

    /** four verdicts, and the two the siblings carry are absent on purpose */
    @Test
    fun theEnumIsNarrowedNotCopied() {
        // Asserted, not commented: "deliberately fewer" and "someone forgot two" look identical
        // in a diff.
        assertEquals(4, HomeListSurface.entries.size)
    }
}
