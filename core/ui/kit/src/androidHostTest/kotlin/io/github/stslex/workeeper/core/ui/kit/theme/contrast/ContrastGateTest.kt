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
 * Enforces [ContrastContract] against the live palette: declared pairs measure up, every slot
 * has a role, no pair is unaccounted for or both declared and excluded. See the v3 redesign spec.
 */
internal class ContrastGateTest {

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

    @Test
    @DisplayName("report: distinct measurements and the worst pair per theme")
    fun report() {
        listOf("DARK" to provideDarkAppColors(), "LIGHT" to provideLightAppColors())
            .forEach { (theme, colors) ->
                val cases = ContrastContract.DECLARED.map { it.toCase(theme, colors) }
                // Role aliasing inflates row counts, so distinct measurements key on VALUES.
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
         * Flattens a translucent slot onto a known backdrop; [WcagContrast] rejects alpha.
         * Background resolves first, then foreground against the result.
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
