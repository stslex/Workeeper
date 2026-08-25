package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Bans `@Suppress("NO_ACTUAL_FOR_EXPECT")` repo-wide: it masks a missing `actual` and builds green
 * while the binding is absent. AST-based, so the bare string in a fixture is not flagged.
 */
class NoActualForExpectSuppressionRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "@Suppress(\"NO_ACTUAL_FOR_EXPECT\") masks a missing `actual` for an " +
            "`expect` declaration (a proven false-green). Fix the real cause — ensure KSP " +
            ">= 2.3.9 generates the actual, or provide the actual — instead of suppressing " +
            "the compiler diagnostic.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)

        if (annotationEntry.shortName?.asString() != SUPPRESS) return

        val suppressed = annotationEntry
            .collectDescendantsOfType<KtStringTemplateExpression>()
            .mapNotNull { it.plainContentOrNull() }
        if (FORBIDDEN_DIAGNOSTIC !in suppressed) return

        report(
            CodeSmell(
                issue,
                Entity.from(annotationEntry),
                "`@Suppress(\"$FORBIDDEN_DIAGNOSTIC\")` is forbidden: it hides a missing " +
                    "`actual` and produces a false-green build. Ensure the `actual` is really " +
                    "generated (KSP >= 2.3.9) or write it explicitly — do not suppress the " +
                    "compiler diagnostic.",
            ),
        )
    }

    /** Content of a plain, non-interpolated string literal, or null for templated strings. */
    private fun KtStringTemplateExpression.plainContentOrNull(): String? {
        if (hasInterpolation()) return null
        return entries.joinToString(separator = "") { it.text }
    }

    private companion object {
        const val SUPPRESS = "Suppress"
        const val FORBIDDEN_DIAGNOSTIC = "NO_ACTUAL_FOR_EXPECT"
    }
}
