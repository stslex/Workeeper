// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.paging.LoadState

/**
 * Which tail a given append state draws. Three states, two drawings — [NONE] is the drawn
 * absence: "конец списка" states nothing beyond what is already visible.
 */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/**
 * The tail decision, extracted as a pure function **because no golden can see it.**
 *
 * Measured, not assumed: with the branch inlined in the screen's `LazyListScope` block, deleting the `LoadState.Error`
 * case left all 30 goldens byte-identical. The footers themselves are
 * photographed — what was ungated is *when* they appear, and a whole-screen golden cannot reach
 * an append-error state: Paparazzi renders one frame of a `PagingData.from` source, which never
 * appends and never fails. A silently truncated list was one deleted line from being
 * indistinguishable from a finished one again, which is the failure the error footer exists to
 * prevent.
 *
 * Same class as the list's bottom clearance, same remedy (§27, "a golden image gates only what a
 * single static frame contains"): name the thing the picture cannot contain and assert the value
 * directly. `PagingTailKindTest` is the gate; `pagingTail` in the screen is dispatch only.
 */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
