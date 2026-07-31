// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * What Home's recent-session region draws when it has no rows.
 *
 * ## Four verdicts, not six — and the narrowing is `feature/archive`'s, not a new decision
 *
 * The two list screens' `listSurface` carries `FILTERED_EMPTY` and `SELECTION_EMPTY`. **Home has no
 * tag filter and no selection mode**, so those two are unreachable here and are not declared:
 * copying the enum whole would ship branches that cannot be entered, which is exactly the reasoning
 * `ArchiveListSurface` already recorded when it made the same cut for the same two reasons.
 *
 * ## Why Home needs it at all
 *
 * Home's predicate was `!isLoading && activeSession == null && recent.isEmpty()` over an unpaged
 * ten-row snapshot, with `isLoading` folding in `isRecentLoaded`. Under a `Pager` that snapshot is
 * gone and the list reports through `LoadState`, so without a selector Home reaches B22 by the
 * same route the three paged screens did: no rows *and* no empty state during the first page.
 *
 * ## What is different here, and it is not the enum
 *
 * On the list screens the empty region **is** the screen's body. On Home it is one band inside a
 * body that also carries the active-session banner and the start card, and the start card is drawn
 * whenever no session is running. So [EMPTY] renders a block with **no action of its own**: the
 * CTA the list screens put on their empty state is already on the card directly above it, and
 * `AppEmptyState` draws a button only when label *and* handler are non-null — a mechanism the
 * drawing's own §26 row cites as load-bearing rather than incidental.
 *
 * ## Why this is a function and not an `if` in the screen
 *
 * No golden can see a selector. Paparazzi renders one settled frame of a `PagingData.from` source,
 * which never leaves refresh unsettled and never fails, so a whole-screen golden cannot enter two
 * of these four verdicts. §27: name the thing the picture cannot contain and assert it directly.
 * `HomeListSurfaceTest` is the gate.
 */
internal enum class HomeListSurface {
    /** Rows. The empty region draws nothing. */
    CONTENT,

    /** Refresh has not settled. The paging spinner, where row 1 will land. */
    LOADING,

    /** The first page failed. Reason plus retry, at the same position, unruled. */
    REFRESH_ERROR,

    /** Settled, and there is genuinely no history yet. */
    EMPTY,
}

/** Pure, so it can be asserted without a screen. */
internal fun homeListSurface(
    itemCount: Int,
    loadState: CombinedLoadStates,
): HomeListSurface = when {
    itemCount > 0 -> HomeListSurface.CONTENT
    loadState.refresh is LoadState.Loading -> HomeListSurface.LOADING
    loadState.refresh is LoadState.Error -> HomeListSurface.REFRESH_ERROR
    else -> HomeListSurface.EMPTY
}
