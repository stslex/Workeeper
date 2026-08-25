// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [NumericFontFamilyOnLocalizedTextRule]: Archivo Expanded has no Cyrillic, so a
 * translatable string set in it renders tofu in `values-ru`.
 */
internal class NumericFontFamilyOnLocalizedTextRuleTest {

    private val rule = NumericFontFamilyOnLocalizedTextRule()

    @Test
    fun `stringResource rendered in a numeric style is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text
            import androidx.compose.ui.res.stringResource

            fun Row() {
                Text(
                    text = stringResource(R.string.feature_live_workout_reps_suffix),
                    style = AppUi.typography.numeric.body,
                )
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "A localized string in the numeric family must be flagged.")
        assertTrue(
            findings.first().message.contains("Cyrillic"),
            "The message must say why, not just that: it is a coverage problem, not a style preference.",
        )
    }

    @Test
    fun `stringResource in the timer alias is flagged`() {
        // `AppUi.typography.timer` aliases `numeric.display` — a third spelling of the family.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text
            import androidx.compose.ui.res.stringResource

            fun Header() {
                Text(
                    text = stringResource(R.string.feature_live_workout_elapsed, elapsed),
                    style = AppUi.typography.timer,
                )
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "The timer alias is the numeric family under another name.")
    }

    @Test
    fun `stringResource with an explicit numericFontFamily is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text
            import androidx.compose.ui.res.stringResource

            fun Row() {
                Text(
                    text = stringResource(R.string.unit_kg),
                    fontFamily = AppUi.typography.numericFontFamily,
                )
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `pluralStringResource in a numeric style is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text
            import androidx.compose.ui.res.pluralStringResource

            fun Row(count: Int) {
                Text(
                    text = pluralStringResource(R.plurals.reps, count, count),
                    style = AppUi.typography.numeric.meta,
                )
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `BasicText is covered too`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.foundation.text.BasicText
            import androidx.compose.ui.res.stringResource

            fun Row() {
                BasicText(
                    text = stringResource(R.string.unit_kg),
                    style = AppUi.typography.numeric.body,
                )
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `digits in a numeric style pass`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text

            fun Timer(elapsed: String) {
                Text(
                    text = elapsed,
                    style = AppUi.typography.numeric.display,
                )
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "Digits are exactly what the numeric family is for.")
    }

    @Test
    fun `a localized string in the text family passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text
            import androidx.compose.ui.res.stringResource

            fun Label() {
                Text(
                    text = stringResource(R.string.unit_kg),
                    style = AppUi.typography.bodyMedium,
                )
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `a localized string in the mono family passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            import androidx.compose.material3.Text
            import androidx.compose.ui.res.stringResource

            fun Unit_() {
                Text(
                    text = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
                    style = AppUi.typography.mono.meta,
                )
            }
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "IBM Plex Mono covers Cyrillic in full, so units may be localized there.",
        )
    }

    @Test
    fun `a non-Text call is ignored`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.live_workout.ui

            fun Chart() {
                drawLabel(
                    text = stringResource(R.string.unit_kg),
                    style = AppUi.typography.numeric.body,
                )
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "The rule is about rendered text, not arbitrary calls.")
    }
}
