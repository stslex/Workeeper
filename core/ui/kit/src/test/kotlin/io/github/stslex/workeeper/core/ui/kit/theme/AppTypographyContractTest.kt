// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The type scale asserted as *numbers*, not as pixels.
 *
 * `TypeSpecimenGoldenTest` renders the whole scale and catches any change to it. What it cannot
 * do is say what the change was *supposed* to be: a golden locks in what **is**, so a wrong
 * baseline is green forever (v3 spec §10.2, and it has shipped twice). Re-recording a specimen
 * is one command, which makes an accidental weight or tracking edit exactly as easy to bless as
 * an intended one.
 *
 * So the values that carry design decisions are stated here as well. The two gates fail
 * differently and that is the point: the golden says "the picture moved", this says "it moved
 * away from the declared value". Both must be updated deliberately.
 *
 * Only decision-bearing values are pinned. The six sizes and line heights are not — they are
 * already one list of named constants in [AppTypography]'s own file, and duplicating them here
 * would assert that a constant equals itself.
 */
internal class AppTypographyContractTest {

    private val typography = provideAppTypography()

    @Test
    @DisplayName("every heading rung of the text family is SemiBold — B2, mockup h1..h4")
    fun headingRungsAreSemiBold() {
        val text = typography.text
        assertEquals(FontWeight.SemiBold, text.display.fontWeight, "text.display")
        assertEquals(FontWeight.SemiBold, text.title.fontWeight, "text.title")
        assertEquals(FontWeight.SemiBold, text.section.fontWeight, "text.section")
    }

    @Test
    @DisplayName("the text rungs stay Regular — 600 is a heading treatment, not a global bump")
    fun textRungsStayRegular() {
        val text = typography.text
        assertEquals(FontWeight.Normal, text.body.fontWeight, "text.body")
        assertEquals(FontWeight.Normal, text.meta.fontWeight, "text.meta")
        assertEquals(FontWeight.Normal, text.caption.fontWeight, "text.caption")
    }

    @Test
    @DisplayName("SemiBold is below WCAG's bold boundary, so no contrast slot reclassifies")
    fun semiBoldIsNotWcagBold() {
        assertEquals(WCAG_BOLD_WEIGHT_FLOOR, FontWeight.Bold.weight, "FontWeight.Bold")
        assertEquals(SEMI_BOLD_WEIGHT, FontWeight.SemiBold.weight, "FontWeight.SemiBold")
        assertEquals(
            true,
            FontWeight.SemiBold.weight < WCAG_BOLD_WEIGHT_FLOOR,
            "600 must stay under the 700 floor, or TypeSlot.SECTION would loosen to 3:1",
        )
    }

    @Test
    @DisplayName("the numeric family is one weight on every rung — Archivo ships only 700")
    fun numericIsBoldThroughout() {
        typography.numeric.rungs().forEach { (name, style) ->
            assertEquals(FontWeight.Bold, style.fontWeight, "numeric.$name")
        }
    }

    @Test
    @DisplayName("the mono family carries no heading weight — no mono selector is a heading")
    fun monoIsRegularThroughout() {
        typography.mono.rungs().forEach { (name, style) ->
            assertEquals(FontWeight.Normal, style.fontWeight, "mono.$name")
        }
    }

    @Test
    @DisplayName("every numeric rung sets tnum — v3 spec C1, Archivo's digits are proportional")
    fun everyNumericRungIsTabular() {
        typography.numeric.rungs().forEach { (name, style) ->
            assertEquals(TABULAR_FIGURES, style.fontFeatureSettings, "numeric.$name")
        }
    }

    @Test
    @DisplayName("the timer slot is the 34sp numeric rung, tabular and in Archivo — B5")
    fun timerSlotIsTheNumericDisplayRung() {
        val timer = typography.timer
        assertEquals(typography.numeric.display, timer, "timer must alias numeric.display")
        assertEquals(TIMER_SIZE, timer.fontSize, "timer.fontSize")
        assertEquals(TABULAR_FIGURES, timer.fontFeatureSettings, "timer must carry tnum")
        assertEquals(typography.numericFontFamily, timer.fontFamily, "timer.fontFamily")
    }

    @Test
    @DisplayName("the title rung is tracked at -0.39sp — B4, the mockups' -.015em at 26sp")
    fun titleRungCarriesHeadingTracking() {
        assertEquals(TITLE_TRACKING, typography.text.title.letterSpacing, "text.title")
    }

    @Test
    @DisplayName("tracking is applied to exactly one (family, rung) pair, and no other")
    fun nothingElseIsTracked() {
        val default = TextStyle.Default.letterSpacing
        val tracked = listOf(
            "text" to typography.text,
            "numeric" to typography.numeric,
            "mono" to typography.mono,
        ).flatMap { (family, styles) ->
            styles.rungs().map { (rung, style) -> "$family.$rung" to style.letterSpacing }
        }.filter { (slot, spacing) ->
            spacing != if (slot.endsWith(".caption")) CAPTION_TRACKING else default
        }
        assertEquals(
            listOf("text.title" to TITLE_TRACKING),
            tracked,
            "only text.title may deviate; caption's 0.5sp is the pre-existing default",
        )
    }

    private companion object {

        /** WCAG 1.4.3's "14pt bold" is 700; 500 and 600 are both below it. */
        const val WCAG_BOLD_WEIGHT_FLOOR = 700

        const val SEMI_BOLD_WEIGHT = 600

        const val TABULAR_FIGURES = "tnum"

        /** `-.015em` at 26sp. The mockups' screen-title tracking, converted. */
        val TITLE_TRACKING = (-0.39).sp

        /** Predates B4 and applies to all three families. */
        val CAPTION_TRACKING = 0.5.sp

        /** The mockups draw the timer at 32px; the ladder rounds it onto the 34 rung. */
        val TIMER_SIZE = 34.sp

        fun AppTypeStyles.rungs(): List<Pair<String, TextStyle>> = listOf(
            "display" to display,
            "title" to title,
            "section" to section,
            "body" to body,
            "meta" to meta,
            "caption" to caption,
        )
    }
}
