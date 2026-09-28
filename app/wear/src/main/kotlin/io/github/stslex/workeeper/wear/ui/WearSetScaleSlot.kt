// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

internal data class WearSetScaleSlot(
    val firstSet: Int,
    val lastSet: Int,
    val completed: Boolean,
    val current: Boolean,
)

internal fun wearSetScaleSlots(current: Int, total: Int): List<WearSetScaleSlot> {
    require(total > 0 && current in 1..total)
    val count = minOf(total, MAX_VISIBLE_SET_PILLS)
    return List(count) { index ->
        // Counts are protocol Ints; Long products keep Int.MAX_VALUE ranges from wrapping.
        val first = (index.toLong() * total / count).toInt() + 1
        val last = ((index + 1L) * total / count).toInt()
        WearSetScaleSlot(
            firstSet = first,
            lastSet = last,
            completed = last < current,
            current = current in first..last,
        )
    }
}

private const val MAX_VISIBLE_SET_PILLS = 8
