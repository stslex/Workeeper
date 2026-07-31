// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The selector, which no golden can see — Home's half of B22.
 *
 * Every verdict is covered, **including [HomeListSurface.CONTENT]**, which renders no empty region
 * at all: "draws nothing" is the outcome a missing branch also produces, so it is the case where a
 * green test proves least unless it is written deliberately.
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

    @Test
    @DisplayName("rows win over everything, including an unsettled refresh")
    fun rowsWin() {
        assertEquals(HomeListSurface.CONTENT, surface(itemCount = 2))
        assertEquals(HomeListSurface.CONTENT, surface(itemCount = 2, refresh = LoadState.Loading))
    }

    @Test
    @DisplayName("an unsettled refresh with no rows is loading, not empty — B22")
    fun coldOpenIsLoading() {
        // Home reached B22 by a different route from the three paged screens: its predicate was
        // `!isLoading && activeSession == null && recent.isEmpty()` over a ten-row snapshot, with
        // `isLoading` folding in an `isRecentLoaded` flag. Same outcome — no rows and the empty
        // state suppressed together — and the flag is gone, so this verdict is what replaces it.
        assertEquals(HomeListSurface.LOADING, surface(refresh = LoadState.Loading))
    }

    @Test
    @DisplayName("a failed first page is its own verdict, not loading and not empty")
    fun coldOpenErrorIsItsOwnVerdict() {
        assertEquals(
            HomeListSurface.REFRESH_ERROR,
            surface(refresh = LoadState.Error(IllegalStateException("boom"))),
        )
    }

    @Test
    @DisplayName("settled with no rows is the empty state")
    fun settledAndEmpty() {
        assertEquals(HomeListSurface.EMPTY, surface())
    }

    @Test
    @DisplayName("four verdicts, and the two the siblings carry are absent on purpose")
    fun theEnumIsNarrowedNotCopied() {
        // `ListSurface` on the two list screens has six values; this has four. Home has no tag
        // filter and no selection mode, so `FILTERED_EMPTY` and `SELECTION_EMPTY` are unreachable
        // here — copying the enum whole would ship two branches nothing can enter, which is what
        // `ArchiveListSurface` already declined to do for the same two reasons.
        //
        // Asserted rather than left to a comment, because "we deliberately have fewer" and "someone
        // forgot two" look identical in a diff.
        assertEquals(4, HomeListSurface.entries.size)
    }
}
