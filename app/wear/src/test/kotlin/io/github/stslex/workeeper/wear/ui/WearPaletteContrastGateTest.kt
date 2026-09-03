// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Gate G5 of the Wear controller redesign spec §7: every foreground/background pair used for
 * text meets 4.5:1 and every stroke meets 3:1, computed from [WearPalette] with the WCAG 2.x
 * formula — never sampled from pixels.
 *
 * [WearPalette.pillPending] on [WearPalette.screen] is deliberately not declared: the pending
 * pill is a decorative fill whose information is repeated as text directly below the set scale
 * (spec §4), the same exemption the phone palette grants `borderSubtle` and `donefill`.
 *
 * Red when any declared text role is pointed at `#627587`, whose ratio on black is below 4.5:1.
 */
internal class WearPaletteContrastGateTest {

    @Test
    @DisplayName("every declared text pair meets 4.5:1 and every declared stroke pair meets 3:1")
    fun everyDeclaredPairMeetsItsThreshold() {
        val failures = (TEXT_PAIRS + STROKE_PAIRS).mapNotNull { pair ->
            val ratio = contrastRatio(pair.foreground, pair.background)
            if (ratio >= pair.threshold) {
                null
            } else {
                "${pair.name}: measured ${"%.2f".format(ratio)}:1, " +
                    "required ${pair.threshold}:1 (${pair.evidence})"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    private data class DeclaredPair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val threshold: Double,
        val evidence: String,
    )

    private companion object {

        const val TEXT_THRESHOLD = 4.5
        const val STROKE_THRESHOLD = 3.0

        val TEXT_PAIRS = listOf(
            text(
                name = "textPrimary on screen",
                foreground = WearPalette.textPrimary,
                background = WearPalette.screen,
                evidence = "status word, exercise name, editor value",
            ),
            text(
                name = "textSecondary on screen",
                foreground = WearPalette.textSecondary,
                background = WearPalette.screen,
                evidence = "set words, editor field label, completion progress",
            ),
            text(
                name = "textMuted on screen",
                foreground = WearPalette.textMuted,
                background = WearPalette.screen,
                evidence = "the word beneath the outlined complete button, disabled editor controls",
            ),
            text(
                name = "error on screen",
                foreground = WearPalette.error,
                background = WearPalette.screen,
                evidence = "field-error line",
            ),
            text(
                name = "textPrimary on card",
                foreground = WearPalette.textPrimary,
                background = WearPalette.card,
                evidence = "value-card numerals",
            ),
            text(
                name = "textSecondary on card",
                foreground = WearPalette.textSecondary,
                background = WearPalette.card,
                evidence = "value-card headers",
            ),
            text(
                name = "textMuted on cardInactive",
                foreground = WearPalette.textMuted,
                background = WearPalette.cardInactive,
                evidence = "read-only card headers and values",
            ),
            text(
                name = "onAccent on textPrimary",
                foreground = WearPalette.onAccent,
                background = WearPalette.textPrimary,
                evidence = "the accent surface: filled complete button, filled editor steppers",
            ),
        )

        val STROKE_PAIRS = listOf(
            stroke(
                name = "stroke on screen",
                foreground = WearPalette.stroke,
                background = WearPalette.screen,
                evidence = "outlined complete-button border",
            ),
            stroke(
                name = "stroke on cardInactive",
                foreground = WearPalette.stroke,
                background = WearPalette.cardInactive,
                evidence = "read-only card outline on its own fill",
            ),
            stroke(
                name = "textPrimary on screen",
                foreground = WearPalette.textPrimary,
                background = WearPalette.screen,
                evidence = "current-set pill ring, hollow connection dot ring",
            ),
        )

        fun text(name: String, foreground: Color, background: Color, evidence: String) =
            DeclaredPair(name, foreground, background, TEXT_THRESHOLD, evidence)

        fun stroke(name: String, foreground: Color, background: Color, evidence: String) =
            DeclaredPair(name, foreground, background, STROKE_THRESHOLD, evidence)

        /** WCAG 2.x contrast ratio of two opaque colours, transcribed from the specification. */
        fun contrastRatio(a: Color, b: Color): Double {
            val luminanceA = relativeLuminance(a)
            val luminanceB = relativeLuminance(b)
            return (max(luminanceA, luminanceB) + 0.05) / (min(luminanceA, luminanceB) + 0.05)
        }

        fun relativeLuminance(color: Color): Double {
            require(color.alpha == 1f) { "The palette is opaque by construction." }
            val argb = color.toArgb()
            return 0.2126 * linearise((argb shr 16) and 0xFF) +
                0.7152 * linearise((argb shr 8) and 0xFF) +
                0.0722 * linearise(argb and 0xFF)
        }

        fun linearise(byte: Int): Double {
            val channel = byte / 255.0
            return if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }
    }
}
