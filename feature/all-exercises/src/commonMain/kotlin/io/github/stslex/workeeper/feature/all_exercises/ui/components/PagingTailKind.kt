// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.paging.LoadState

/** Which tail a given append state draws. [NONE] is the drawn absence: no footer at all. */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/**
 * The tail decision, pure so it can be asserted — no golden can reach an append-error state.
 * `PagingTailKindTest` is the gate; `pagingTail` in the screen is dispatch only.
 */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
