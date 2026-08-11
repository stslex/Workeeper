// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * What an archive tab draws when it has no rows.
 *
 * ## Why this exists on a screen the v3 arc has not reached
 *
 * `isPagingEmpty` here is the same predicate as the two list screens' `isEmptyAndIdle` spelled
 * longhand: it requires `refresh`, `append` **and** `prepend` to be `NotLoading`, so during the
 * first page load the tab has no rows *and* its empty state is suppressed — nothing at all is
 * drawn (**B22**). The defect is shipped and does not get to wait for this screen's turn in the
 * redesign; the two list screens' fix is worth nothing to a user who opens Archive.
 *
 * ## Four verdicts, not six
 *
 * The sibling screens' selector carries `FILTERED_EMPTY` and `SELECTION_EMPTY`. **This screen has
 * no tag filter and no selection mode**, so those verdicts are unreachable here and are not
 * declared: copying the enum whole would have shipped two branches that cannot be entered, which
 * is the shape of dead code this arc has already deleted once.
 *
 * ## Why this is a function and not an `if` in the screen
 *
 * No golden can see a selector — Paparazzi renders one settled frame of a `PagingData.from` source,
 * which never leaves refresh unsettled and never fails. §27: name the thing the picture cannot
 * contain and assert it directly. `ArchiveListSurfaceTest` is the gate.
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
