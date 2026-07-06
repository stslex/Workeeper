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
 * Bans `@Suppress("NO_ACTUAL_FOR_EXPECT")` anywhere in the repo.
 *
 * `NO_ACTUAL_FOR_EXPECT` is a Kotlin *compiler* diagnostic: it fires when an `expect`
 * declaration has no matching `actual` for some target. Suppressing it does not fix the
 * missing `actual` — it makes the module compile while the binding is silently absent.
 *
 * This is not hypothetical. Probe-2 caught exactly this FALSE GREEN: KSP 2.3.6 silently
 * skipped Room's native (`actual`) codegen, and a `@Suppress("NO_ACTUAL_FOR_EXPECT")`
 * masked the missing `actual` so the build went green while broken. The fix is to make the
 * `actual` real — ensure KSP >= 2.3.9 (which generates it) or provide the `actual` by hand —
 * never to suppress the diagnostic.
 *
 * Scope: repo-wide, all source sets. Validated safe as a blanket ban — at the time this rule
 * was introduced (Phase C.0) the repo had ZERO `expect`/`actual` declarations and ZERO
 * `NO_ACTUAL_FOR_EXPECT` suppressions outside disposable probe modules, so there is no
 * legitimate location for this suppression. If a future KMP `expect` legitimately relies on
 * plugin-generated `actual`s, correct toolchain configuration makes the diagnostic disappear
 * on its own; the suppression stays banned.
 *
 * Detection is AST-based: it flags a `@Suppress` / `@file:Suppress` annotation entry (in any
 * argument form — positional strings or the `names = [...]` array) whose arguments contain the
 * literal `"NO_ACTUAL_FOR_EXPECT"`. A file that merely contains the string as text (e.g. a
 * rule fixture inside a triple-quoted literal) is not an annotation entry and is not flagged.
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
