// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * What the list region draws when it has no rows (`pass2d.html` `#s-empty`, spec §26). The order
 * is load-bearing: an unsettled refresh is unknown rather than empty, and the mode beats its cause.
 */
internal enum class ListSurface {
    /** Rows. The empty region draws nothing. */
    CONTENT,

    /** Refresh has not settled. The `.pfoot` spinner, where row 1 will land. */
    LOADING,

    /** The first page failed. Its own verdict — [LOADING] would be a lie, [FIRST_RUN] worse. */
    REFRESH_ERROR,

    /** No rows, nothing done to cause it. The drawn first-run empty, with its glyph tile. */
    FIRST_RUN,

    /** A tag filter matched nothing. No tile; the action clears the filter. */
    FILTERED_EMPTY,

    /** Selection is running and the list emptied under it. No tile; the marks survive. */
    SELECTION_EMPTY,
}

/**
 * Whether this verdict crossfades in the empty region (spec §26). GUARD: never widen it to
 * `CONTENT` or `LOADING` — goldens compose LOADING first and would catch it mid-flight.
 */
internal val ListSurface.crossfades: Boolean
    get() = when (this) {
        ListSurface.CONTENT, ListSurface.LOADING -> false
        ListSurface.REFRESH_ERROR,
        ListSurface.FIRST_RUN,
        ListSurface.FILTERED_EMPTY,
        ListSurface.SELECTION_EMPTY,
        -> true
    }

/**
 * Pure, so it can be asserted without a screen. [itemCount] and [loadState] come from
 * `LazyPagingItems`; [filterActive] and [selecting] from the store's state.
 */
internal fun listSurface(
    itemCount: Int,
    loadState: CombinedLoadStates,
    filterActive: Boolean,
    selecting: Boolean,
): ListSurface = when {
    itemCount > 0 -> ListSurface.CONTENT
    loadState.refresh is LoadState.Loading -> ListSurface.LOADING
    loadState.refresh is LoadState.Error -> ListSurface.REFRESH_ERROR
    selecting -> ListSurface.SELECTION_EMPTY
    filterActive -> ListSurface.FILTERED_EMPTY
    else -> ListSurface.FIRST_RUN
}
