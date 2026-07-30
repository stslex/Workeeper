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
 * A colour animation must never use `Color.Transparent` as an endpoint.
 *
 * ## Why this is a rule and not a review note
 *
 * `Color.Transparent` is transparent **black**, and `animateColorAsState` interpolates in Oklab —
 * so a tween between a visible colour and `Color.Transparent` does not fade the colour *out*, it
 * travels toward black while the alpha drops. The mid-frames composite darker than **both**
 * endpoints.
 *
 * The failure is invisible to every gate this repo has. Both endpoints are correct, so a golden
 * pair photographs two correct pictures; Paparazzi renders one settled frame, so no golden can
 * reach the transit at all; and the excursion is large only in **light** theme, because on a dark
 * page the path to black stays inside the endpoints. Measured on the shipped palette, four sites
 * were flashing at once and had been through a full visual review:
 *
 * ```
 *   list row lift        #F6F7F9..#FFFFFF  ->  #ACADAE   (+0.290)
 *   top-bar icon press   #F6F7F9..#EFF1F4  ->  #A9AAAB   (+0.275)
 *   settings row press   #F6F7F9..#EFF1F4  ->  #A9AAAB   (+0.275)
 *   set-mark record fill #FFFFFF..#F97316  ->  #B09381   (+0.286)
 * ```
 *
 * The fix is one call: `theme.fadedOut()`, which is the same colour at zero alpha, so only alpha
 * moves and no mid-frame is a colour neither endpoint contains. It also needs no knowledge of what
 * is behind the surface, which "fade to the background colour" would.
 *
 * ## Scope, and what this deliberately does not catch
 *
 * PSI-only — this repo's custom rules run without type resolution, so a type-resolving rule finds
 * nothing in CI. It matches the literal text `Color.Transparent` inside a call whose callee is a
 * colour animation. Laundering the value through a local (`val gone = Color.Transparent` then
 * `animateColorAsState(gone)`) is not detected, and neither is a colour animation reached through
 * an alias. That residual is covered by `FadeOutTest`, which measures the excursion at every known
 * fade site — the two together are the guard, and neither is sufficient alone.
 *
 * A static, non-animated `Color.Transparent` is fine and is not matched: a surface that is simply
 * invisible never interpolates, so there is no mid-frame to be wrong.
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
