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
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * O2 — the numeric display family must never receive a translatable string.
 *
 * Archivo has **zero** Cyrillic coverage: none of the 55 Cyrillic characters the shipped
 * `values-ru` corpus uses. (Measured from the bundled file's `cmap`: `« » · × — … → •` *are*
 * present — an earlier version of this note listed them as missing, which was wrong. The gap
 * is Cyrillic letters and nothing else.) A `stringResource` routed through it renders as tofu
 * in Russian, or silently falls back to a face that is not the one the design asked for.
 * Neither failure is visible to the compiler, and neither is visible to a reviewer reading an
 * English screenshot — which is exactly why this is a rule and not a comment.
 *
 * Flags a `Text` / `BasicText` call that combines the numeric family with a localized argument:
 *
 * ```
 * Text(
 *     text = stringResource(R.string.reps_suffix),          // localized
 *     style = AppUi.typography.numeric.body,                // Archivo — no Cyrillic
 * )
 * ```
 *
 * Digits and the `: . , - + / %` separators are the whole intended scope, so
 * `Text(text = elapsed, style = AppUi.typography.numeric.display)` passes.
 *
 * **Known limitation.** This is a PSI-only rule, so it matches on the argument text of the
 * call itself. Laundering the style through a local — `val s = AppUi.typography.numeric.body`
 * then `Text(..., style = s)` — is not detected. The visual half of the guard covers what this
 * cannot: `CyrillicTextGoldenTest` renders real `values-ru` strings, so a family swap that
 * produces tofu moves pixels and fails the screenshot gate. The two together are the guard;
 * neither is sufficient alone.
 */
class NumericFontFamilyOnLocalizedTextRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "The numeric font family (Archivo) has no Cyrillic coverage and " +
            "must never render a translatable string.",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeExpression?.text ?: return
        if (callee !in TEXT_COMPOSABLES) return

        val arguments = expression.valueArguments
        if (arguments.none { it.usesNumericFamily() }) return
        if (arguments.none { it.isLocalized() }) return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "This $callee renders a localized string in the numeric font family. Archivo " +
                    "has no Cyrillic glyphs, so this shows tofu in ru. Use " +
                    "AppUi.typography.text or .mono for anything translatable; the numeric " +
                    "family takes digits and : . , - + / % only.",
            ),
        )
    }

    private fun KtValueArgument.usesNumericFamily(): Boolean {
        val text = getArgumentExpression()?.text ?: return false
        return NUMERIC_MARKERS.any { marker -> marker in text }
    }

    private fun KtValueArgument.isLocalized(): Boolean {
        val text = getArgumentExpression()?.text ?: return false
        return LOCALIZED_MARKERS.any { marker -> marker in text }
    }

    private companion object {
        val TEXT_COMPOSABLES = setOf("Text", "BasicText")

        /**
         * The family itself, any style built on it, and every alias onto it.
         *
         * `typography.timer` is the third spelling and it is not optional: it is the *name the
         * session screen is told to call*, so leaving it out would mean the rule was blind to
         * the one call site it exists to guard. Any future alias onto the numeric family
         * belongs here on the same day it is added.
         */
        val NUMERIC_MARKERS = listOf(
            "numericFontFamily",
            "typography.numeric",
            "typography.timer",
        )

        val LOCALIZED_MARKERS = listOf("stringResource(", "pluralStringResource(")
    }
}
