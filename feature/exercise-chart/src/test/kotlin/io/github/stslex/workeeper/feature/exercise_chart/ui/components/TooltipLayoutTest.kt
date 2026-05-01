// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("MagicNumber")
internal class TooltipLayoutTest {

    // Pixel values chosen to mirror the production density-resolved constants on a 1x
    // density: anchorGap 8, edgeMargin 8, notchHeight 8, cornerRadius 10, safeMargin 4.
    private val spec = TooltipLayoutSpec(
        anchorGapPx = 8f,
        edgeMarginPx = 8f,
        notchHeightPx = 8f,
        cornerRadiusPx = 10f,
        notchSafeMarginPx = 4f,
    )

    private val canvas = Size(800f, 400f)
    private val cardWidth = 200f
    private val cardHeight = 80f

    @Test
    fun `anchor in safe span draws notch under the point`() {
        // Anchor mid-canvas so the card centres on it and the notch sits 100 px into
        // the card — well inside the [14, 186] safe zone.
        val layout = computeTooltipLayout(
            anchor = Offset(400f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )

        assertTrue(layout.showNotch)
        assertEquals(100f, layout.notchOffsetX)
    }

    @Test
    fun `anchor near left edge clamps card and hides notch in corner zone`() {
        // anchor.x = 12 → card centred would be at -88, clamps to edgeMargin (8).
        // notchOffsetX = 12 - 8 = 4, which lies in the [0, 14) corner zone.
        val layout = computeTooltipLayout(
            anchor = Offset(12f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )

        assertEquals(8f, layout.cardLeft)
        assertFalse(layout.showNotch)
    }

    @Test
    fun `anchor near right edge clamps card and hides notch in corner zone`() {
        // Symmetric to the left-edge case — card clamps to (canvas - cardWidth - margin).
        val layout = computeTooltipLayout(
            anchor = Offset(canvas.width - 6f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )

        // cardLeft = canvas.width - cardWidth - edgeMargin = 800 - 200 - 8 = 592.
        assertEquals(592f, layout.cardLeft)
        assertFalse(layout.showNotch)
    }

    @Test
    fun `anchor exactly at left safe-zone boundary draws notch (inclusive)`() {
        // Safe zone min = corner + safeMargin = 14. Card sits centred at anchor 200,
        // so cardLeft = 100; pick anchor = cardLeft + 14 = 114 to land on the boundary.
        val layout = computeTooltipLayout(
            anchor = Offset(114f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec.copy(),
        )

        // Card stays where the centred-on-anchor calc puts it (114 - 100 = 14).
        assertEquals(14f, layout.cardLeft)
        // notchOffsetX = anchor.x - cardLeft = 114 - 14 = 100 — well inside the safe zone.
        // To verify the *boundary* itself, build a clamped case where notchOffsetX equals
        // exactly 14: anchor 22 → card centred at -78 clamps to 8, notchOffsetX = 22 - 8 = 14.
        val boundary = computeTooltipLayout(
            anchor = Offset(22f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )
        assertEquals(8f, boundary.cardLeft)
        assertEquals(14f, boundary.notchOffsetX)
        assertTrue(boundary.showNotch)

        // Reference assertion so the unused `layout` variable still earns its keep:
        // mid-card anchor obviously draws the notch.
        assertTrue(layout.showNotch)
    }

    @Test
    fun `anchor one pixel into the corner zone hides the notch`() {
        // notchOffsetX = 13 (one less than the inclusive boundary of 14) → hidden.
        val layout = computeTooltipLayout(
            anchor = Offset(21f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )
        assertEquals(8f, layout.cardLeft)
        assertEquals(13f, layout.notchOffsetX)
        assertFalse(layout.showNotch)
    }

    @Test
    fun `flips below when anchor is too close to top edge`() {
        // anchor.y is small enough that aboveTop (= y - cardH - gap - notchH) falls below
        // edgeMargin → flipBelow = true.
        val layout = computeTooltipLayout(
            anchor = Offset(400f, 20f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )
        assertTrue(layout.flipBelow)
        // cardTop sits below the anchor by (gap + notchHeight) = 16.
        assertEquals(36f, layout.cardTop)
    }

    @Test
    fun `keeps card above when there is room`() {
        // anchor.y far from the top → aboveTop > edgeMargin, no flip.
        val layout = computeTooltipLayout(
            anchor = Offset(400f, 250f),
            canvasSize = canvas,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )
        assertFalse(layout.flipBelow)
        // cardTop = anchor.y - cardHeight - anchorGap - notchHeight = 250 - 80 - 8 - 8 = 154.
        assertEquals(154f, layout.cardTop)
    }

    @Test
    fun `safe-zone collapses on a card narrower than two corner radii plus margins`() {
        // cardWidth 24 < 2 * (corner + margin) = 28 → notchSafeMin > notchSafeMax. The
        // function must report showNotch = false rather than draw a notch in an inverted
        // range.
        val tinyCardWidth = 24f
        val layout = computeTooltipLayout(
            anchor = Offset(400f, 250f),
            canvasSize = canvas,
            cardWidth = tinyCardWidth,
            cardHeight = cardHeight,
            spec = spec,
        )
        assertFalse(layout.showNotch)
    }
}
