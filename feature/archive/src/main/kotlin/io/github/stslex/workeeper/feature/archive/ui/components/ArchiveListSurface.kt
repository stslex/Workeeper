// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * What an archive tab draws when it has no rows. Four verdicts, not the siblings' six — no tag
 * filter and no selection mode here; `ArchiveListSurfaceTest` is the gate.
 */
internal enum class ArchiveListSurface {
    /** Rows. The empty region draws nothing. */
    CONTENT,

    /** Refresh has not settled. The paging spinner, where row 1 will land. */
    LOADING,

    /** The first page failed. Reason plus retry, at the same position. */
    REFRESH_ERROR,

    /** Settled, and there is genuinely nothing archived. The drawn empty state. */
    EMPTY,
}

/** Pure, so it can be asserted without a screen. */
internal fun archiveListSurface(
    itemCount: Int,
    loadState: CombinedLoadStates,
): ArchiveListSurface = when {
    itemCount > 0 -> ArchiveListSurface.CONTENT
    loadState.refresh is LoadState.Loading -> ArchiveListSurface.LOADING
    loadState.refresh is LoadState.Error -> ArchiveListSurface.REFRESH_ERROR
    else -> ArchiveListSurface.EMPTY
}
