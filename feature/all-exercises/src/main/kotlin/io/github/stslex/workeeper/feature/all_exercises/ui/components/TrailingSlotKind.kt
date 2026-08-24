// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

/**
 * What the row's fixed-width trailing slot holds. [EMPTY] keeps the slot for an unselected row in
 * selection mode — collapsing it reflows every row (spec §26).
 */
internal enum class TrailingSlotKind { CHEVRON, CHECK, EMPTY }

/**
 * The slot decision, pure so it can be asserted — no golden can see which kind is chosen. GUARD:
 * `isSelected` is tested first, so a selected row keeps its check whatever `isSelecting` says.
 */
internal fun trailingSlotKind(
    isSelected: Boolean,
    isSelecting: Boolean,
): TrailingSlotKind = when {
    isSelected -> TrailingSlotKind.CHECK
    isSelecting -> TrailingSlotKind.EMPTY
    else -> TrailingSlotKind.CHEVRON
}
