// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Every verdict of the selector, including [ArchiveListSurface.CONTENT], which draws nothing. */
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

    @Test
    fun `an unsettled refresh with no rows is loading, not empty`() {
        assertEquals(ArchiveListSurface.LOADING, surface(refresh = LoadState.Loading))
    }

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
