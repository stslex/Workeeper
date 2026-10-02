// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.LoadState

/** Which tail an append state draws; [NONE] is the drawn absence. */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/** The tail decision as a pure function, because no golden can see it (§27). */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
