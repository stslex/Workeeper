// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

/**
 * What the row's fixed-width trailing slot holds. An unselected row in selection mode loses the
 * chevron but keeps the slot, since collapsing it reflows every row (§26 "Selection mode").
 */
internal enum class TrailingSlotKind { CHEVRON, CHECK, EMPTY }

/**
 * The slot decision, pure so a unit test can gate it — no golden can see which kind is chosen.
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
