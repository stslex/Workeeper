// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import io.github.stslex.workeeper.core.ui.kit.theme.AppColors
import io.github.stslex.workeeper.core.ui.kit.theme.provideDarkAppColors
import io.github.stslex.workeeper.core.ui.kit.theme.provideLightAppColors
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The contrast gate.
 *
 * Reads the contract in [ContrastContract] and enforces it against the live palette. The three
 * tests correspond to the three parts of the contract, and the third is the reason this is a
 * gate rather than a report:
 *
 *  1. [every_declared_pair_meets_its_threshold] — the measurements.
 *  2. [every_slot_has_a_declared_role] — a new slot cannot default into being ignored.
 *  3. [every_foreground_surface_pair_is_declared_or_excluded] — no combination of existing
 *     slots can sit unaccounted for. Without this the gate would only check what it already
 *     knew about.
 *  4. [no_pair_is_both_declared_and_excluded] — an exclusion that a declaration contradicts has
 *     a false premise, and would be silently masking its whole family.
 *
 * ## What this gate does not do
 *
 * It reads [ContrastContract] and the palette. It does **not** read production call sites. So:
 *
 *  - Adding a slot, or painting one existing slot on another in a way the contract has not
 *     accounted for, fails here. That is (2) and (3).
 *  - Adding a screen that paints an *already declared* pair is fine and stays green, correctly —
 *     the pair is measured.
 *  - Adding a screen that paints a pair currently covered by an **exclusion** stays green
 *     **wrongly**, because the exclusion's premise is a claim about layout ("molten never
 *     appears on `field`") that this test cannot re-verify. Each exclusion records the evidence
 *     it rested on so the claim can be re-checked by a human; (4) catches the case where the
 *     contract itself has already contradicted one.
 *
 * Closing that last gap needs call-site analysis — a detekt rule that resolves
 * `AppUi.colors.<slot>` against the enclosing surface — which is a bigger tool than this.
 * Until then: **an exclusion is an assertion about the UI, and it ages.**
 */
internal class ContrastGateTest {

    // ---------------------------------------------------------------------------------------
    // Part (a) — the declared triples are measured and must pass.
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredPairs")
    @DisplayName("every declared pair meets the threshold its type slot demands")
    fun every_declared_pair_meets_its_threshold(case: Case) {
        val ratio = WcagContrast.contrastRatio(case.foreground, case.background)
        assertTrue(
            ratio >= case.declared.typeSlot.threshold,
            """
            |${case.theme} ${case.declared.foreground} on ${case.declared.background}
            |  measured  ${WcagContrast.format(ratio)}:1
            |  required  ${case.declared.typeSlot.threshold}:1 (${case.declared.typeSlot} — ${case.declared.typeSlot.why})
            |  evidence  ${case.declared.evidence}
            |  colours   ${case.foreground.hex()} on ${case.background.hex()}
            """.trimMargin(),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Part (b) — every slot is classified.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("every palette slot has a declared role, and no role names a slot that is gone")
    fun every_slot_has_a_declared_role() {
        val scanned = PaletteInventory.slots(provideDarkAppColors()).keys
        val declared = ContrastContract.ROLES.keys

        val unclassified = scanned - declared
        val stale = declared - scanned

        assertTrue(
            unclassified.isEmpty(),
            "New palette slot(s) with no declared role: $unclassified. Add each to " +
                "ContrastContract.ROLES — a slot that is not classified is a slot that is " +
                "never measured.",
        )
        assertTrue(
            stale.isEmpty(),
            "ContrastContract.ROLES names slot(s) that no longer exist: $stale.",
        )
    }

    // ---------------------------------------------------------------------------------------
    // Part (c) — the point. Enumerate the full product; nothing may be silently unaccounted for.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("every foreground x surface combination is either declared or excluded")
    fun every_foreground_surface_pair_is_declared_or_excluded() {
        val roles = ContrastContract.ROLES
        val foregrounds = roles.filterValues { it == SlotRole.FOREGROUND || it == SlotRole.BOTH }.keys
        val surfaces = roles.filterValues { it == SlotRole.SURFACE || it == SlotRole.BOTH }.keys

        val declaredPairs = ContrastContract.DECLARED
            .map { it.foreground to it.background }
            .toSet()

        val unaccounted = foregrounds
            .flatMap { fg -> surfaces.map { bg -> fg to bg } }
            .filter { (fg, bg) -> fg != bg }
            .filterNot { it in declaredPairs }
            .filterNot { (fg, bg) -> ContrastContract.EXCLUSIONS.any { it.matches(fg, bg) } }

        assertTrue(
            unaccounted.isEmpty(),
            buildString {
                appendLine("${unaccounted.size} foreground/surface pair(s) are neither declared nor excluded.")
                appendLine()
                appendLine("This is the check that stops a new screen from introducing an unverified")
                appendLine("colour pairing in silence. For each pair below, either:")
                appendLine("  - add a ContrastContract.DECLARED row with the type slot it is painted at, or")
                appendLine("  - add a ContrastContract.EXCLUSIONS rule saying why it cannot occur.")
                appendLine()
                unaccounted.forEach { (fg, bg) -> appendLine("  $fg on $bg") }
            },
        )
    }

    @Test
    @DisplayName("no pair is both declared and excluded")
    fun no_pair_is_both_declared_and_excluded() {
        val contradictions = ContrastContract.DECLARED
            .map { it.foreground to it.background }
            .distinct()
            .mapNotNull { (fg, bg) ->
                ContrastContract.EXCLUSIONS
                    .firstOrNull { it.matches(fg, bg) }
                    ?.let { "$fg on $bg — excluded because: ${it.reason}" }
            }

        assertTrue(
            contradictions.isEmpty(),
            buildString {
                appendLine("A declared pair is also matched by an exclusion rule.")
                appendLine()
                appendLine("An exclusion claims a pair cannot occur. A declaration says it does.")
                appendLine("Both cannot be true, and the exclusion is the dangerous half — it is")
                appendLine("a rule, so it is silently suppressing every other pair in its family")
                appendLine("too. Narrow or delete the rule; do not delete the declaration.")
                appendLine()
                contradictions.forEach { appendLine("  $it") }
            },
        )
    }

    // ---------------------------------------------------------------------------------------
    // Reporting — distinct measurements, not row counts.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("report: distinct measurements and the worst pair per theme")
    fun report() {
        listOf("DARK" to provideDarkAppColors(), "LIGHT" to provideLightAppColors())
            .forEach { (theme, colors) ->
                val cases = ContrastContract.DECLARED.map { it.toCase(theme, colors) }
                // Role aliasing inflates row counts: `accent`, `textPrimary` and
                // `accentTintedForeground` are all v3 `max`, so one fact can be printed three
                // times. Distinct measurements are keyed by the colour VALUES, not the names.
                val distinct = cases
                    .map { it.foreground.toArgb() to it.background.toArgb() }
                    .toSet()
                val worst = cases.minByOrNull {
                    WcagContrast.contrastRatio(it.foreground, it.background) /
                        it.declared.typeSlot.threshold
                }
                println("CONTRAST $theme rows=${cases.size} distinctMeasurements=${distinct.size}")
                worst?.let {
                    val ratio = WcagContrast.contrastRatio(it.foreground, it.background)
                    println(
                        "CONTRAST $theme tightest: ${it.declared.foreground} on " +
                            "${it.declared.background} = ${WcagContrast.format(ratio)}:1 " +
                            "(needs ${it.declared.typeSlot.threshold}:1)",
                    )
                }
                cases.sortedBy {
                    WcagContrast.contrastRatio(it.foreground, it.background)
                }.take(5).forEach {
                    val ratio = WcagContrast.contrastRatio(it.foreground, it.background)
                    println(
                        "CONTRAST $theme  ${WcagContrast.format(ratio)}:1 " +
                            "(needs ${it.declared.typeSlot.threshold}) " +
                            "${it.declared.foreground} on ${it.declared.background}",
                    )
                }
            }
    }

    internal data class Case(
        val theme: String,
        val declared: ContrastContract.Declared,
        val foreground: Color,
        val background: Color,
    ) {

        override fun toString(): String = buildString {
            append("$theme ${declared.foreground} on ${declared.background}")
            if (declared.over != ContrastContract.PAGE) append(" over ${declared.over}")
            append(" @${declared.typeSlot} — ${declared.evidence}")
        }
    }

    private companion object {

        /**
         * Alpha is composited, never guessed.
         *
         * [WcagContrast] rejects a translucent colour outright, because a ratio for a
         * see-through foreground is meaningless until it has been flattened onto a *known*
         * backdrop — and flattening onto the wrong one yields a plausible, wrong number.
         *
         * Two slots are genuinely translucent: the molten wash (9% / 11%) and the destructive
         * wash (12%). Both are painted directly on a card, so `surfaceTier1` is the backdrop
         * the composite uses. A translucent *foreground* on a translucent *background* would be
         * ambiguous and there is no such declared pair; if one ever appears, this resolves the
         * background first and the foreground against the result.
         */
        private fun Color.flatten(over: Color): Color =
            if (alpha == 1f) this else compositeOver(over)

        fun ContrastContract.Declared.toCase(theme: String, colors: AppColors): Case {
            val slots = PaletteInventory.slots(colors)
            // The production dialog idiom, reproduced rather than approximated.
            val backdrop = when (over) {
                ContrastContract.DIALOG ->
                    if (colors.isDark) slots.getValue("surfaceTier1") else slots.getValue("surfaceTier2")

                else -> slots.getValue(over)
            }
            val bg = slots.getValue(background).flatten(over = backdrop)
            val fg = slots.getValue(foreground).flatten(over = bg)
            return Case(theme, this, fg, bg)
        }

        @JvmStatic
        fun declaredPairs(): List<Case> =
            listOf("DARK" to provideDarkAppColors(), "LIGHT" to provideLightAppColors())
                .flatMap { (theme, colors) ->
                    ContrastContract.DECLARED.map { it.toCase(theme, colors) }
                }

        fun Color.hex(): String = "#%06X".format(toArgb().toLong() and 0xFFFFFFL)
    }
}
