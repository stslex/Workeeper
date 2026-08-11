// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Proves [WcagContrast] before it is pointed at an unknown palette. An unproven calculator
 * measuring an unknown palette tells you nothing: every number in the contrast report is only
 * as trustworthy as this file.
 *
 * Expected values are independently computed from the WCAG 2.1 formula (not from this
 * implementation) and asserted to 1e-9, so a wrong exponent, a wrong coefficient, a wrong
 * piecewise threshold or a missing linearisation all fail here rather than silently shifting
 * the report.
 */
internal class WcagContrastTest {

    @Nested
    @DisplayName("relative luminance")
    inner class RelativeLuminance {

        @Test
        fun `white is 1 and black is 0`() {
            assertEquals(1.0, WcagContrast.relativeLuminance(Color.White), TOLERANCE)
            assertEquals(0.0, WcagContrast.relativeLuminance(Color.Black), TOLERANCE)
        }

        /**
         * At full saturation the linearised channel is exactly 1.0, so each primary's luminance
         * *is* its coefficient. This pins all three coefficients independently — a swapped
         * red/blue weight cannot survive it.
         */
        @ParameterizedTest(name = "L(#{0}) = {1}")
        @CsvSource(
            "FF0000, 0.2126",
            "00FF00, 0.7152",
            "0000FF, 0.0722",
        )
        fun `each primary luminance equals its WCAG coefficient`(hex: String, expected: Double) {
            assertEquals(expected, WcagContrast.relativeLuminance(hex.toColor()), TOLERANCE)
        }

        @Test
        fun `mid grey uses the gamma branch`() {
            // #808080: ((128/255 + 0.055) / 1.055) ^ 2.4 applied to all three channels.
            assertEquals(
                0.215860500113899,
                WcagContrast.relativeLuminance("808080".toColor()),
                TOLERANCE,
            )
        }

        @Test
        fun `very dark grey uses the linear branch`() {
            // #050505: 5/255 = 0.0196 is below the 0.03928 threshold, so c / 12.92 applies.
            assertEquals(
                0.001517634917744,
                WcagContrast.relativeLuminance("050505".toColor()),
                TOLERANCE,
            )
        }

        @Test
        fun `translucent colours are rejected rather than silently measured`() {
            val error = assertThrows(IllegalArgumentException::class.java) {
                WcagContrast.relativeLuminance(Color.Black.copy(alpha = 0.5f))
            }
            assertTrue(error.message.orEmpty().contains("opaque"))
        }

        @Test
        fun `channel extraction matches the declared hex bytes`() {
            // Guards the byte extraction itself: a swapped red/blue shift produces a plausible
            // luminance for greys and a silently wrong one for everything else.
            val argb = Color(0xFF6EB7AB).toArgb()
            assertEquals(0x6E / 255.0, WcagContrast.channel(argb, WcagContrast.RED_SHIFT), TOLERANCE)
            assertEquals(
                0xB7 / 255.0,
                WcagContrast.channel(argb, WcagContrast.GREEN_SHIFT),
                TOLERANCE,
            )
            assertEquals(
                0xAB / 255.0,
                WcagContrast.channel(argb, WcagContrast.BLUE_SHIFT),
                TOLERANCE,
            )
            assertEquals(1f, Color(0xFF6EB7AB).alpha, FLOAT_TOLERANCE)
        }

        @Test
        fun `the whole declared palette round-trips through the 8 bit extraction`() {
            // Every colour in AppColors is declared as an opaque 8-bit hex literal, so byte
            // extraction must be lossless for all of them — not just for the sample above.
            listOf(0xFF0E0F0E, 0xFF6EB7AB, 0xFFE8E8E5, 0xFFDEAA62, 0xFF4F5052, 0xFFFAFAF8)
                .forEach { literal ->
                    val argb = Color(literal).toArgb()
                    assertEquals(literal.toInt(), argb, "lossy round-trip for ${literal.toString(16)}")
                }
        }
    }

    @Nested
    @DisplayName("contrast ratio")
    inner class ContrastRatio {

        @Test
        fun `black on white is the maximum 21 to 1`() {
            assertEquals(
                MAX_RATIO,
                WcagContrast.contrastRatio(Color.Black, Color.White),
                TOLERANCE,
            )
        }

        @ParameterizedTest(name = "#{0} against itself is 1:1")
        @ValueSource(strings = ["000000", "FFFFFF", "6EB7AB", "1B1C1A", "DEAA62"])
        fun `identical colours are 1 to 1`(hex: String) {
            val color = hex.toColor()
            assertEquals(1.0, WcagContrast.contrastRatio(color, color), TOLERANCE)
        }

        /**
         * Published boundary anchors. #767676 and #777777 straddle the 4.5:1 line by one byte —
         * they are the sharpest available check that the curve, not just the endpoints, is right.
         * #595959 straddles 7:1 the same way.
         */
        @ParameterizedTest(name = "#{0} on #{1} = {2}")
        @CsvSource(
            "767676, FFFFFF, 4.542224959605",
            "777777, FFFFFF, 4.478089453577",
            "595959, FFFFFF, 7.004729208036",
            "808080, FFFFFF, 3.949439648049",
            "FF0000, FFFFFF, 3.998476770754",
            "0000FF, FFFFFF, 8.592471358429",
            "0000FF, 000000, 2.444000000000",
            "00FF00, 000000, 15.304000000000",
            "050505, 000000, 1.030352698355",
        )
        fun `known anchors match the WCAG reference values`(
            foreground: String,
            background: String,
            expected: Double,
        ) {
            assertEquals(
                expected,
                WcagContrast.contrastRatio(foreground.toColor(), background.toColor()),
                TOLERANCE,
            )
        }

        @ParameterizedTest(name = "ratio(#{0}, #{1}) is order-independent")
        @CsvSource(
            "6EB7AB, 0E0F0E",
            "FFFFFF, 000000",
            "B5B6B0, 16171A",
        )
        fun `the ratio is symmetric`(first: String, second: String) {
            assertEquals(
                WcagContrast.contrastRatio(first.toColor(), second.toColor()),
                WcagContrast.contrastRatio(second.toColor(), first.toColor()),
                TOLERANCE,
            )
        }

        @Test
        fun `no pair of opaque colours can leave the 1 to 21 range`() {
            val samples = (0..0xFF step 0x11).map { Color(0xFF000000L or (it * 0x010101L)) }
            samples.forEach { first ->
                samples.forEach { second ->
                    val ratio = WcagContrast.contrastRatio(first, second)
                    assertTrue(ratio >= 1.0 - TOLERANCE, "ratio below 1: $ratio")
                    assertTrue(ratio <= MAX_RATIO + TOLERANCE, "ratio above 21: $ratio")
                }
            }
        }

        /**
         * Negative control. The anchors above are only meaningful if they can *discriminate* —
         * a green test against a broken implementation is worthless. Skipping linearisation is
         * the single most common way this formula is got wrong, and it must move the number a
         * long way, not a rounding-error's worth.
         */
        @Test
        fun `the anchors discriminate against a non-linearised implementation`() {
            val naive = naiveContrastWithoutLinearisation("767676".toColor(), Color.White)
            val correct = WcagContrast.contrastRatio("767676".toColor(), Color.White)

            assertEquals(2.047801147227, naive, TOLERANCE)
            assertEquals(4.542224959605, correct, TOLERANCE)
            assertTrue(
                correct - naive > 2.0,
                "linearisation must dominate the result; delta was ${correct - naive}",
            )
            assertNotEquals(WcagBand.of(naive), WcagBand.of(correct))
        }

        private fun naiveContrastWithoutLinearisation(a: Color, b: Color): Double {
            fun luminance(color: Color): Double {
                val argb = color.toArgb()
                return 0.2126 * WcagContrast.channel(argb, WcagContrast.RED_SHIFT) +
                    0.7152 * WcagContrast.channel(argb, WcagContrast.GREEN_SHIFT) +
                    0.0722 * WcagContrast.channel(argb, WcagContrast.BLUE_SHIFT)
            }
            val first = luminance(a)
            val second = luminance(b)
            return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
        }
    }

    @Nested
    @DisplayName("band classification")
    inner class Bands {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "21.0,  7:1",
            "7.01,  7:1",
            "7.0,   7:1",
            "6.999, 4.5:1",
            "4.5,   4.5:1",
            "4.499, 3:1",
            "3.0,   3:1",
            "2.999, fails 3:1",
            "1.0,   fails 3:1",
        )
        fun `a ratio lands in the strongest band it attains`(ratio: Double, expected: String) {
            assertEquals(expected, WcagBand.of(ratio).label)
        }

        @Test
        fun `the real boundary greys land on the expected side of 4_5`() {
            assertEquals(
                WcagBand.AA_NORMAL_TEXT,
                WcagBand.of(WcagContrast.contrastRatio("767676".toColor(), Color.White)),
            )
            assertEquals(
                WcagBand.AA_LARGE_OR_UI,
                WcagBand.of(WcagContrast.contrastRatio("777777".toColor(), Color.White)),
            )
        }
    }

    @Nested
    @DisplayName("formatting")
    inner class Formatting {

        @ParameterizedTest(name = "{0} formats as {1}")
        @CsvSource(
            "21.0,             21.00",
            "4.542224959605,   4.54",
            "4.4996,           4.49",
            "4.5,              4.50",
            "1.030352698355,   1.03",
        )
        fun `formatting truncates so it can never overstate a ratio`(
            ratio: Double,
            expected: String,
        ) {
            assertEquals(expected, WcagContrast.format(ratio))
        }
    }

    private companion object {

        /** Anchors are exact to well beyond double precision; 1e-9 leaves no room to hide. */
        const val TOLERANCE = 1e-9
        const val FLOAT_TOLERANCE = 1e-6f
        const val MAX_RATIO = 21.0

        fun String.toColor(): Color = Color(0xFF000000L or trim().toLong(radix = 16))
    }
}
