// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.paging.LoadState

/**
 * Which tail a given append state draws. Three states, two drawings — [NONE] is the drawn
 * absence: "конец списка" states nothing beyond what is already visible.
 */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/**
 * The tail decision, pure so `PagingTailKindTest` can gate it — a whole-screen golden cannot
 * reach an append-error state, so `pagingTail` in the screen is dispatch only.
 */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
