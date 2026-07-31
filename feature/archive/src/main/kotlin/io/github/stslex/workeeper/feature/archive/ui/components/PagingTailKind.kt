// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.paging.LoadState

/**
 * Which tail a given append state draws. Three states, two drawings — [NONE] is the drawn
 * absence: "конец списка" states nothing beyond what is already visible.
 */
internal enum class PagingTailKind { LOADING, ERROR, NONE }

/**
 * The tail decision, extracted as a pure function **because no golden can see it** (§27).
 *
 * Third screen, third copy, and the duplication is deliberate — §27's MATCH rule wants a test on
 * each side of a behavioural parity claim rather than one test and an assertion of sameness.
 *
 * This screen is the one that made the general finding legible (archive-delta §3.2). All three
 * paged screens read `loadState.append` **exactly once**, and in all three it was the same line —
 * inside an is-this-empty predicate, never to draw a footer. So «пагинация уже есть в тренировках,
 * упражнениях и архиве» was true three times and meant only that pages arrive: **the append state
 * was known to every one of them and spent on the wrong question.** A screen that reads `append` to
 * decide emptiness has already met the value it needed for the footer and walked past it.
 */
internal fun pagingTailKind(append: LoadState): PagingTailKind = when (append) {
    is LoadState.Loading -> PagingTailKind.LOADING
    is LoadState.Error -> PagingTailKind.ERROR
    is LoadState.NotLoading -> PagingTailKind.NONE
}
