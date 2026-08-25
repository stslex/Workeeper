// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * What Home's recent-session region draws when it has no rows. Four verdicts only: Home has no
 * tag filter and no selection mode, so the list screens' other two are unreachable here.
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
