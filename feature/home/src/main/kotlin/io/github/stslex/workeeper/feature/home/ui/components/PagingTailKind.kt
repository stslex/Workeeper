// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.LoadState

/**
 * Which tail a given append state draws. Three states, two drawings (§26 "Paging tails") — [NONE]
 * is the **drawn absence**: "конец списка" states nothing beyond what is already visible.
 */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/**
 * The tail decision, extracted as a pure function **because no golden can see it.**
 *
 * Measured on `all-exercises`, not assumed here: with this branch inlined in the screen's
 * `LazyListScope` block, deleting the `LoadState.Error` case left all 30 of that module's goldens
 * byte-identical. The footers themselves are photographed; what a picture cannot reach is *when*
 * they appear, because Paparazzi renders one frame of a `PagingData.from` source that never
 * appends and never fails. A silently truncated list is one deleted line from being
 * indistinguishable from a finished one.
 *
 * **Duplicated from the sibling modules rather than shared**, following `PagingTailKind` there and
 * `ArchiveListSurface`: the screens draw one skeleton but are separate modules, and §27's MATCH
 * rule wants a test on **each** side of a behavioural parity claim rather than one test plus an
 * assertion of sameness. `HomePagingTailKindTest` is this side's.
 */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
