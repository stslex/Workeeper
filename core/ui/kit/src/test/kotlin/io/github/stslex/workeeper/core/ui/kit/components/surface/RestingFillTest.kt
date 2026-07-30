// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.surface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.stslex.workeeper.core.ui.kit.theme.provideDarkAppColors
import io.github.stslex.workeeper.core.ui.kit.theme.provideLightAppColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * The mid-transition frame, which no golden can see.
 *
 * `liftedSurface` cross-fades its fill, and `rowSelected` / `rowUnselectedInSelection` photograph
 * the two settled **endpoints** — both of which were always correct. What was wrong lived only
 * between them: a row resting at `Color.Transparent` (transparent *black*) and lifting to a white
 * slab passed through mid-grey, because the tween interpolates in Oklab and carries the resting
 * colour's hue with it. Light theme only; dark theme hides it because transparent-black and
 * `#0B0D0F` are both dark.
 *
 * Same class and same remedy as `LIST_BOTTOM_CLEARANCE` and `pagingTailKind` (§27): the value the
 * picture cannot contain is named and asserted directly.
 *
 * The tween is sampled with Compose's own [lerp], not a reimplementation of it — a hand-rolled RGB
 * midpoint would agree with the fix and disagree with the framework, which is the one way this test
 * could pass while the screen still flashes.
 */
internal class RestingFillTest {

    /** Sample points across the 260ms fill tween. The endpoints are the goldens' job. */
    private val midpoints = listOf(0.25f, 0.5f, 0.75f)

    /**
     * How far the mid-transition frame may sit from the page it is resting on, per channel.
     *
     * The resting row draws nothing of its own, so *every* frame of the fade in ought to be between
     * the page and the slab. 0.05 allows the slab's own lightening to show through and nothing else;
     * the defect this catches was 0.29 off (`#ACACAD` against `#F6F7F9`), an order of magnitude out.
     */
    private val tolerance = 0.05f

    @Test
    fun `a fully transparent resting colour takes the RGB of what is behind it`() {
        val behind = Color(0xFFF6F7F9)
        val repaired = restingFill(Color.Transparent, behind)
        assertEquals(0f, repaired.alpha, "it must stay invisible")
        assertEquals(behind.red, repaired.red)
        assertEquals(behind.green, repaired.green)
        assertEquals(behind.blue, repaired.blue)
    }

    @Test
    fun `a translucent resting colour is passed through untouched`() {
        val chosen = Color(0x80FF0000)
        assertEquals(chosen, restingFill(chosen, Color(0xFFF6F7F9)))
    }

    @Test
    fun `an opaque resting colour is passed through untouched`() {
        val chosen = Color(0xFF123456)
        assertEquals(chosen, restingFill(chosen, Color(0xFFF6F7F9)))
    }

    @Test
    fun `light theme - the fade in never darkens below the page`() = assertNoDarkExcursion(
        colors = provideLightAppColors().surfaceTier0 to provideLightAppColors().surfaceTier2,
    )

    @Test
    fun `dark theme - the fade in never darkens below the page`() = assertNoDarkExcursion(
        colors = provideDarkAppColors().surfaceTier0 to provideDarkAppColors().surfaceTier2,
    )

    /**
     * The regression this file exists for, stated as the failing case rather than only as the fixed
     * one: with `Color.Transparent` unrepaired, the light-theme midpoint composites to `#ACACAD`
     * over a `#F6F7F9` page. If [restingFill] ever stops repairing, this is what ships.
     */
    @Test
    fun `the unrepaired transparent black is what the repair exists to prevent`() {
        val page = provideLightAppColors().surfaceTier0
        val slab = provideLightAppColors().surfaceTier2
        val bad = composite(lerp(Color.Transparent, slab, 0.5f), over = page)
        assertTrue(
            page.red - bad.red > 0.2f,
            "expected the unrepaired tween to darken by >0.2; it darkened by ${page.red - bad.red}",
        )
    }

    private fun assertNoDarkExcursion(colors: Pair<Color, Color>) {
        val (page, slab) = colors
        val resting = restingFill(Color.Transparent, page)
        midpoints.forEach { t ->
            val frame = composite(lerp(resting, slab, t), over = page)
            listOf(
                Triple("red", frame.red, page.red),
                Triple("green", frame.green, page.green),
                Triple("blue", frame.blue, page.blue),
            ).forEach { (name, got, floor) ->
                assertTrue(
                    got >= floor - tolerance,
                    "at t=$t the $name channel fell to $got, below the page's $floor by more " +
                        "than $tolerance — the fill is passing through a darker colour on its way up",
                )
            }
        }
    }

    /** Source-over composite, which is what the row's background does against the page. */
    private fun composite(src: Color, over: Color): Color = Color(
        red = src.red * src.alpha + over.red * (1 - src.alpha),
        green = src.green * src.alpha + over.green * (1 - src.alpha),
        blue = src.blue * src.alpha + over.blue * (1 - src.alpha),
        alpha = 1f,
    ).also { check(abs(it.alpha - 1f) < 1e-6) }
}
