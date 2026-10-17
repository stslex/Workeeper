// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.paging.LoadState

/** Which tail a given append state draws: three states, two drawings; [NONE] draws nothing. */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/**
 * The tail decision, pure because no golden can see it — a picture cannot reach an append state.
 * Duplicated per module rather than shared, so each side carries its own test.
 */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
