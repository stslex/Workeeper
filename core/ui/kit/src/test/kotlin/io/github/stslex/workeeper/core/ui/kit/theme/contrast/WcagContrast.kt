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
 * WCAG 2.x relative luminance and contrast ratio, transcribed from the specification text
 * rather than pulled from a library, so the measurement carries no hidden dependency and
 * every constant is auditable against the spec.
 *
 * Relative luminance (WCAG 2.1, "relative luminance" definition):
 * ```
 * for each sRGB channel c in [0,1]:
 *     c_lin = c / 12.92                       if c <= 0.03928
 *     c_lin = ((c + 0.055) / 1.055) ^ 2.4     otherwise
 * L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin
 * ```
 *
 * Contrast ratio (WCAG 2.1, "contrast ratio" definition):
 * ```
 * ratio = (L_lighter + 0.05) / (L_darker + 0.05)
 * ```
 *
 * Note on the piecewise threshold: WCAG 2.x writes `0.03928`, which is *not* the mathematically
 * exact sRGB inflection point (`0.04045`). The spec value is used here deliberately — the goal is
 * to reproduce what a WCAG conformance checker reports, not to re-derive sRGB.
 *
 * For 8-bit input the choice is moot, and provably so: `0.03928 * 255 = 10.016` and
 * `0.04045 * 255 = 10.315`, so no byte value falls between the two thresholds. Byte 10 takes the
 * linear branch under both and byte 11 takes the gamma branch under both. Every ratio in this
 * report is bit-identical either way.
 *
 * Alpha is rejected rather than guessed: a contrast ratio for a translucent foreground is
 * only meaningful once it has been composited over a *known* backdrop, and compositing the
 * wrong backdrop silently produces a plausible number. Callers must composite first.
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
     *
     * Channels are taken from the packed 8-bit sRGB bytes rather than from `Color.red`/`.green`/
     * `.blue`. Those accessors return `Float`, and `128 / 255f` widened to `Double` is
     * `0.50196081399…` against an exact `0.50196078431…` — an error near 1e-8 that survives into
     * the ratio. It could never move a two-decimal report, but it *would* make the reference
     * anchors in [WcagContrastTest] unverifiable at a tolerance tight enough to catch a genuinely
     * wrong implementation. WCAG itself defines the formula over 8-bit channel values, so
     * reading the bytes is both exact and the more literal transcription.
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

    /**
     * Two decimals, rounded **down**. Truncation rather than rounding is deliberate: a ratio
     * of 4.4996 must not be printed as "4.50" next to a band column that says it failed.
     */
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
