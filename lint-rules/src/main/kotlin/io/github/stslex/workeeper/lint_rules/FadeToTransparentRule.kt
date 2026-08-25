// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * A colour animation must never end at `Color.Transparent` — it is transparent black, so Oklab
 * mid-frames composite darker than both endpoints. Use `fadedOut()`. See lint-rules.md.
 */
class FadeToTransparentRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "A colour animation must fade a colour out (`fadedOut()`), never fade to " +
            "`Color.Transparent`, which is transparent black and darkens the mid-frames.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeExpression?.text ?: return
        if (callee !in COLOUR_ANIMATIONS) return

        val offending = expression.valueArguments.firstOrNull { argument ->
            argument.getArgumentExpression()?.text?.contains(TRANSPARENT) == true
        } ?: return

        report(
            CodeSmell(
                issue,
                Entity.from(offending),
                "`Color.Transparent` is transparent BLACK, and $callee interpolates in Oklab — " +
                    "so this fades toward black, not out, and the mid-frames composite darker " +
                    "than both endpoints (measured up to #A9AAAB between #F6F7F9 and #EFF1F4). " +
                    "No golden can see it: Paparazzi renders one settled frame and both " +
                    "endpoints are correct. Use `<theColour>.fadedOut()` instead — same colour, " +
                    "zero alpha, so only alpha moves.",
            ),
        )
    }

    private companion object {
        val COLOUR_ANIMATIONS = setOf("animateColorAsState", "animateColor")
        const val TRANSPARENT = "Color.Transparent"
    }
}
