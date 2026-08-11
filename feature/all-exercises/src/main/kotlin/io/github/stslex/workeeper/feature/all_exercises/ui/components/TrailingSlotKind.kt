// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

/**
 * What the row's fixed-width trailing slot holds. Three kinds, and [EMPTY] is one of them —
 * §26 "Selection mode": an unselected row in selection mode leads nowhere, so it has nothing to
 * promise and loses the chevron, but **keeps the slot**, because collapsing it reflows every row.
 */
internal enum class TrailingSlotKind { CHEVRON, CHECK, EMPTY }

/**
 * The slot decision, extracted as a pure function **because no golden can see the transition it
 * now drives.**
 *
 * Same class and same remedy as [pagingTailKind] (§27, "a golden image gates only what a single
 * static frame contains"), and the amendment to that entry is the reason this exists at all:
 * splitting a surface out so it can be photographed is exactly what makes its *selector*
 * invisible. Under §26's continuity-motion row the slot no longer swaps instantly — it crossfades
 * — and a crossfade has three states to get right where a `when` had three branches to get right.
 * A golden covers what each glyph looks like; nothing covers **which one is chosen**, least of all
 * [EMPTY], the outcome a missing branch produces by accident.
 *
 * Note the deliberate ordering: `isSelected` is tested **first**, so a selected row draws its
 * check whatever `isSelecting` says. The two flags are supplied independently by the screen, and
 * "selected but not selecting" must not fall through to [EMPTY] — it would blank the mark on the
 * very row the user just tapped.
 */
internal fun trailingSlotKind(
    isSelected: Boolean,
    isSelecting: Boolean,
): TrailingSlotKind = when {
    isSelected -> TrailingSlotKind.CHECK
    isSelecting -> TrailingSlotKind.EMPTY
    else -> TrailingSlotKind.CHEVRON
}
