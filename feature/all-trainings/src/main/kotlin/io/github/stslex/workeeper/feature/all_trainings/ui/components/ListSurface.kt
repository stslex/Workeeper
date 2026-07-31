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

/**
 * Whether this verdict takes part in the empty region's crossfade (§26, continuity motion).
 *
 * **The four drawn blocks do; [CONTENT] and [LOADING] do not, and the boundary is drawn between
 * *what the user acts on* and *what the data does*.** The pair the continuity row actually named is
 * [SELECTION_EMPTY] ⇄ [FILTERED_EMPTY] — a tag filter emptied under an active selection, then the
 * mode left with the filter still on. Both are blocks the user is looking at, and one replaces the
 * other on their gesture, with no path between frames. That is the class.
 *
 * [CONTENT] and [LOADING] are not peers of those. [CONTENT] is the *absence* of this region — what
 * replaces a block is rows, which carry their own transit (`Modifier.animateItem`), so the frame the
 * block leaves is not an empty one. [LOADING] is the absence of *knowledge*, and this file already
 * says so ("an unsettled refresh is not an empty list, it is an unknown one"); resolving an unknown
 * into a verdict is not one drawn thing becoming another.
 *
 * **This is a bounded exclusion, not a derivation, and it is recorded as one.** By the bare
 * membership test `LOADING → FIRST_RUN` is in class: delete the animation and the spinner is
 * replaced instantly. It is excluded anyway, for the reason above — and the exclusion has a second,
 * measured consequence that is the reason this property exists as a named, assertable thing rather
 * than an `if` at the call site.
 *
 * **A transition keyed on a verdict that settles asynchronously makes a settled golden photograph a
 * transient.** `collectAsLazyPagingItems()` always begins at `itemCount = 0` with `refresh =
 * Loading`, so every whole-screen golden composes [LOADING] first and reaches its real verdict one
 * frame later. Paparazzi renders **one** frame and its composable `snapshot` overload takes no
 * clock offset, so an `AnimatedContent` spanning that flip is photographed at t = 0 — showing the
 * outgoing spinner at full alpha and the block at zero. Measured: ten goldens across both list
 * screens went red that way, and the picture was of `Loading` on a screen whose golden is named for
 * the empty state. Keeping [LOADING] out of the key means the `AnimatedContent` mounts *fresh* at
 * its real verdict, with current and target equal, so no transition exists to be caught mid-flight.
 *
 * See §27. **Do not widen this to `true` for [CONTENT] or [LOADING].**
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
