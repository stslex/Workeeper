// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The selector, which no golden can see — this screen's half of B22.
 *
 * Every verdict is covered, **including [ArchiveListSurface.CONTENT]**, which renders no empty
 * region at all: "draws nothing" is the outcome a missing branch also produces, so it is the case
 * where a green test proves least unless it is written deliberately.
 */
internal class ArchiveListSurfaceTest {

    private fun states(refresh: LoadState) = CombinedLoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(false),
        append = LoadState.NotLoading(false),
        source = LoadStates(refresh, LoadState.NotLoading(false), LoadState.NotLoading(false)),
    )

    private fun surface(itemCount: Int = 0, refresh: LoadState = LoadState.NotLoading(false)) =
        archiveListSurface(itemCount, states(refresh))

    @Test
    fun `rows win over everything`() {
        assertEquals(ArchiveListSurface.CONTENT, surface(itemCount = 2))
        assertEquals(ArchiveListSurface.CONTENT, surface(itemCount = 2, refresh = LoadState.Loading))
    }

    /**
     * B22's fix for this screen. Before it, an unsettled refresh with no rows drew **nothing**:
     * `isPagingEmpty` demanded `refresh is NotLoading`, so the tab had no rows and its empty state
     * was suppressed by the same condition.
     */
    @Test
    fun `an unsettled refresh with no rows is loading, not empty`() {
        assertEquals(ArchiveListSurface.LOADING, surface(refresh = LoadState.Loading))
    }

    /** The other half: a failed first page blanked for exactly the same reason. */
    @Test
    fun `a failed first page is its own verdict`() {
        assertEquals(
            ArchiveListSurface.REFRESH_ERROR,
            surface(refresh = LoadState.Error(IllegalStateException("boom"))),
        )
    }

    @Test
    fun `settled with no rows is the empty state`() {
        assertEquals(ArchiveListSurface.EMPTY, surface())
    }
}
