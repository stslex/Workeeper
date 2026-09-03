// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x relative luminance and contrast ratio, transcribed from the specification text.
 * Alpha is rejected rather than guessed: callers composite over a known backdrop first.
 */
internal object WcagContrast {

    private const val SRGB_LINEAR_THRESHOLD = 0.03928
    private const val SRGB_LINEAR_DIVISOR = 12.92
    private const val SRGB_GAMMA_OFFSET = 0.055
    private const val SRGB_GAMMA_SCALE = 1.055
    private const val SRGB_GAMMA_EXPONENT = 2.4

    private const val LUMINANCE_COEFFICIENT_RED = 0.2126
    private const val LUMINANCE_COEFFICIENT_GREEN = 0.7152
    private const val LUMINANCE_COEFFICIENT_BLUE = 0.0722

    /** The +0.05 "flare" term of the WCAG contrast-ratio formula. */
    private const val CONTRAST_FLARE = 0.05

    private const val DISPLAY_SCALE = 100.0

    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val BLUE_SHIFT = 0

    private const val BYTE_MASK = 0xFF
    private const val BYTE_MAX = 255.0

    /** Linearise one sRGB channel expressed in `[0,1]`. */
    fun linearise(channel: Double): Double = if (channel <= SRGB_LINEAR_THRESHOLD) {
        channel / SRGB_LINEAR_DIVISOR
    } else {
        ((channel + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_SCALE).pow(SRGB_GAMMA_EXPONENT)
    }

    /**
     * WCAG relative luminance of an **opaque** colour, in `[0,1]`.
     * Channels come from the packed 8-bit bytes; `Color.red`/`.green`/`.blue` lose about 1e-8.
     */
    fun relativeLuminance(color: Color): Double {
        require(color.alpha == 1f) {
            "relativeLuminance requires an opaque colour; got alpha=${color.alpha}. " +
                "Composite the colour over its backdrop before measuring."
        }
        val argb = color.toArgb()
        return LUMINANCE_COEFFICIENT_RED * linearise(channel(argb, RED_SHIFT)) +
            LUMINANCE_COEFFICIENT_GREEN * linearise(channel(argb, GREEN_SHIFT)) +
            LUMINANCE_COEFFICIENT_BLUE * linearise(channel(argb, BLUE_SHIFT))
    }

    /** One 8-bit sRGB channel of a packed ARGB int, normalised to `[0,1]`. */
    fun channel(argb: Int, shift: Int): Double = ((argb shr shift) and BYTE_MASK) / BYTE_MAX

    /**
     * WCAG contrast ratio between two opaque colours, in `[1,21]`.
     * Symmetric: argument order does not matter.
     */
    fun contrastRatio(a: Color, b: Color): Double {
        val luminanceA = relativeLuminance(a)
        val luminanceB = relativeLuminance(b)
        return (max(luminanceA, luminanceB) + CONTRAST_FLARE) /
            (min(luminanceA, luminanceB) + CONTRAST_FLARE)
    }

    /** Two decimals, rounded down, so 4.4996 is never printed as "4.50" beside a failed band. */
    fun format(ratio: Double): String =
        String.format(Locale.ROOT, "%.2f", floor(ratio * DISPLAY_SCALE) / DISPLAY_SCALE)
}

/**
 * The WCAG 2.x threshold a ratio actually attains — not the one it is required to meet.
 * Ordered strongest first so [of] can return the first satisfied band.
 */
internal enum class WcagBand(val label: String, val threshold: Double) {

    /** WCAG 1.4.6 AAA for normal text. */
    AAA_ENHANCED("7:1", 7.0),

    /** WCAG 1.4.3 AA for normal text; also AAA for large text. */
    AA_NORMAL_TEXT("4.5:1", 4.5),

    /** WCAG 1.4.3 AA for large text; WCAG 1.4.11 for UI components and graphical objects. */
    AA_LARGE_OR_UI("3:1", 3.0),

    /** Below every WCAG threshold. */
    FAILS("fails 3:1", 0.0),
    ;

    companion object {

        fun of(ratio: Double): WcagBand = entries.first { ratio >= it.threshold }
    }
}
