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
 * Flags a `Text` / `BasicText` rendering a localized string in the numeric family, which has no
 * Cyrillic letters. PSI-only, so a style laundered through a local is missed. See design-system.md.
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
         * The family itself, any style built on it, and every alias onto it — an unregistered
         * alias blinds the rule at every call site reached through it.
         */
        val NUMERIC_MARKERS = listOf(
            "numericFontFamily",
            "typography.numeric",
            "typography.timer",
            "typography.dataValue",
        )

        val LOCALIZED_MARKERS = listOf("stringResource(", "pluralStringResource(")
    }
}
