// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsNode

/** Rectangular layout visibility; shape outlines, occlusion and painted pixels are separate checks. */
internal data class WearVisibleBounds(
    val declaredSizeDp: Size,
    val ancestorClippedRect: Rect,
    val screenClippedRect: Rect,
    val density: Float,
) {
    val visibleSizeDp: Size
        get() = Size(screenClippedRect.width / density, screenClippedRect.height / density)

    fun meetsMinimumSize(minimumDp: Float): Boolean =
        visibleSizeDp.width >= minimumDp && visibleSizeDp.height >= minimumDp
}

internal fun SemanticsNode.wearVisibleBounds(screenRectInRoot: Rect): WearVisibleBounds {
    check(layoutInfo.isAttached && layoutInfo.isPlaced) { "Cannot measure a detached or unplaced node" }
    val density = layoutInfo.density.density
    // Compose walks actual clipping ancestors. A sibling viewport must never clip this node.
    val ancestorClipped = boundsInRoot
    val screenClipped = ancestorClipped.intersect(screenRectInRoot).let { intersection ->
        if (intersection.width > 0f && intersection.height > 0f) intersection else Rect.Zero
    }
    return WearVisibleBounds(
        declaredSizeDp = Size(size.width / density, size.height / density),
        ancestorClippedRect = ancestorClipped,
        screenClippedRect = screenClipped,
        density = density,
    )
}
