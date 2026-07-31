// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.stslex.workeeper.core.ui.kit.components.surface.restingFill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The mid-transition frame, which no golden can see — and every site that has one.
 *
 * `FadeToTransparentRule` stops the idiom coming back in a form it can read. This measures the
 * consequence at each real site, which is what covers the forms it cannot read (a value laundered
 * through a local, an aliased animation). The two together are the guard.
 *
 * The tween is sampled with Compose's own [lerp], not a reimplementation of it: a hand-rolled RGB
 * midpoint would agree with the fix and disagree with the framework, which is the one way this file
 * could pass while the screen still flashes.
 */
internal class FadeOutTest {

    /** Sample points across the fade. The endpoints are the goldens' job. */
    private val midpoints = listOf(0.25f, 0.5f, 0.75f)

    /**
     * How far a mid-frame may fall below the darker endpoint, per channel.
     *
     * A cross-fade should stay between its endpoints. 0.02 is measurement slack; the defects this
     * caught were +0.275 to +0.290, an order of magnitude out.
     */
    private val tolerance = 0.02f

    // ---- the rule ------------------------------------------------------------------------------

    @Test
    fun `fadedOut keeps the colour and drops the alpha`() {
        val c = Color(0xFFF97316)
        assertEquals(0f, c.fadedOut().alpha)
        assertEquals(c.red, c.fadedOut().red)
        assertEquals(c.green, c.fadedOut().green)
        assertEquals(c.blue, c.fadedOut().blue)
    }

    @Test
    fun `restingFill repairs a fully transparent rest to the lifted colour faded out`() {
        val lifted = Color(0xFFFFFFFF)
        assertEquals(lifted.fadedOut(), restingFill(Color.Transparent, lifted))
    }

    @Test
    fun `restingFill passes a translucent rest through untouched`() {
        val chosen = Color(0x80FF0000)
        assertEquals(chosen, restingFill(chosen, Color(0xFFFFFFFF)))
    }

    @Test
    fun `restingFill passes an opaque rest through untouched`() {
        val chosen = Color(0xFF123456)
        assertEquals(chosen, restingFill(chosen, Color(0xFFFFFFFF)))
    }

    // ---- every fade site in the app ------------------------------------------------------------

    /**
     * Each site is (name, target, the surface it sits on). Adding a colour animation that fades
     * something out means adding a row here — `FadeToTransparentRule` catches the literal form, and
     * this catches the rest.
     */
    private fun sites(c: AppColors) = listOf(
        Triple("list row lift", c.surfaceTier2, c.surfaceTier0),
        Triple("top-bar icon press", c.surfaceTier1, c.surfaceTier0),
        Triple("settings row press", c.surfaceTier1, c.surfaceTier0),
        Triple("mini icon press", c.borderSubtle, c.surfaceTier2),
        Triple("set-mark fill", c.accent, c.surfaceTier2),
        Triple("set-mark record fill", c.molten.solid, c.surfaceTier2),
    )

    @Test
    fun `light theme - no fade site darkens below both its endpoints`() =
        sites(provideLightAppColors()).forEach { (name, target, behind) ->
            assertNoExcursion(name, target, behind)
        }

    @Test
    fun `dark theme - no fade site darkens below both its endpoints`() =
        sites(provideDarkAppColors()).forEach { (name, target, behind) ->
            assertNoExcursion(name, target, behind)
        }

    /**
     * The regression stated as the failing case, not only as the fixed one.
     *
     * Four light-theme sites were excursing at once when this was found. If [fadedOut] ever stops
     * being used, this is what ships — and nothing else in the repo would notice.
     */
    @Test
    fun `fading to Color Transparent is what these tests exist to prevent`() {
        val c = provideLightAppColors()
        val flashing = sites(c).filter { (_, target, behind) ->
            val mid = composite(lerp(Color.Transparent, target, 0.5f), behind)
            val floor = minOf(behind.red, composite(target, behind).red)
            floor - mid.red > 0.2f
        }
        assertEquals(
            4,
            flashing.size,
            "expected the four measured light-theme excursions; got ${flashing.map { it.first }}",
        )
    }

    private fun assertNoExcursion(name: String, target: Color, behind: Color) {
        val resting = target.fadedOut()
        val floor = minOf(behind.red, composite(target, behind).red)
        midpoints.forEach { t ->
            val frame = composite(lerp(resting, target, t), behind)
            assertTrue(
                frame.red >= floor - tolerance,
                "$name at t=$t composited to ${frame.red}, below both endpoints' $floor — " +
                    "the fade is passing through a colour neither endpoint contains",
            )
        }
    }

    /** Source-over composite, which is what a background modifier does against what is behind it. */
    private fun composite(src: Color, over: Color): Color = Color(
        red = src.red * src.alpha + over.red * (1 - src.alpha),
        green = src.green * src.alpha + over.green * (1 - src.alpha),
        blue = src.blue * src.alpha + over.blue * (1 - src.alpha),
        alpha = 1f,
    )
}
