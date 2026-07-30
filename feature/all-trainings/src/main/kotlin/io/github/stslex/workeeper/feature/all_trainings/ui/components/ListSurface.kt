// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * What the list region draws when it has no rows — `pass2d.html` `#s-empty`, the three blocks
 * added for states reached by an action.
 *
 * ## The predicate this replaces was one branch where four states live
 *
 * `isEmptyAndIdle() && !isSelecting` collapsed everything into "show the first-run empty, or draw
 * nothing". It required `refresh`, `append` **and** `prepend` to be `NotLoading`, so during the
 * first page load the list had no rows *and* the empty state was suppressed — nothing at all was
 * drawn (**B22**). And when a tag filter matched nothing it showed «Здесь появятся тренировки»
 * with a create button, under a filter band the user had just touched.
 *
 * ## The discriminator the drawing rules
 *
 * §26 "List states reached by an action": a glyph tile means the screen is empty **by itself**; no
 * tile means the user arrived by an action they can undo. [FIRST_RUN] has the tile;
 * [FILTERED_EMPTY] and [SELECTION_EMPTY] do not.
 *
 * ## Order, and why it is this order
 *
 * [LOADING] first: an unsettled refresh is not an empty list, it is an unknown one, and every
 * other verdict would be a guess. Then [SELECTION_EMPTY] before [FILTERED_EMPTY], because
 * selection is the *mode* and the filter is the *cause* — the selection block carries the filter
 * recovery inside it (its action is conditional on `filterActive`), so nothing is lost by the mode
 * winning, whereas the reverse would strand a user mid-selection with no word about their marks.
 *
 * ## Why this is a function and not an `if` in the screen
 *
 * No golden can see a selector. Paparazzi renders one settled frame of a `PagingData.from` source,
 * which never reaches a loading or error state, so a whole-screen golden cannot enter three of
 * these five verdicts — and the screen-level "empty" golden was in fact a picture of B22 for
 * exactly that reason. §27: name the thing the picture cannot contain and assert it directly.
 * `ListSurfaceTest` is the gate.
 */
internal enum class ListSurface {
    /** Rows. The empty region draws nothing. */
    CONTENT,

    /** Refresh has not settled. The `.pfoot` spinner, where row 1 will land. */
    LOADING,

    /**
     * The first page failed. **Undrawn** — B22's fourth region: `.perr` draws a failed *append*,
     * and nothing draws a failed *first* page. Mapped to [LOADING] would be a lie and to
     * [FIRST_RUN] a worse one, so it is its own verdict, unrendered, and named where the next
     * reader meets it.
     */
    REFRESH_ERROR,

    /** No rows, nothing done to cause it. The drawn first-run empty, with its glyph tile. */
    FIRST_RUN,

    /** A tag filter matched nothing. No tile; the action clears the filter. */
    FILTERED_EMPTY,

    /** Selection is running and the list emptied under it. No tile; the marks survive. */
    SELECTION_EMPTY,
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
