package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtUserType

/**
 * The second half of the Wear transport privacy gate.
 *
 * `ForbiddenImport` in `lint-rules/detekt.yml` covers the import directive, and nothing else:
 * it visits `KtImportDirective`, so a fully qualified reference carries no import for it to
 * reject. Measured — `:app:wear:detekt` is GREEN on a main source file containing
 * `com.google.android.gms.wearable.Wearable.getMessageClient(context)` while the same module goes
 * RED on the equivalent import. That hole is the whole gate: the specification's blocking privacy
 * gate on sending a workout payload is only as real as the narrowest way around it.
 *
 * This rule closes it at the other end, over references rather than imports, so the two together
 * cover both ways of naming the API. Reporting is limited to the outermost node of a qualified
 * chain, so one reference produces one finding.
 *
 * Reflective reach by string name is deliberately out of scope: it is invisible to the AST, and it
 * still requires the Data Layer on a module's classpath — which is a build-file edit, reviewed as
 * the privacy decision it is. AST-only also means this rule's own test fixtures, which are string
 * literals, are not flagged.
 */
class WearDataLayerApiRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "References to the Wearable Data Layer API ($FORBIDDEN_PACKAGE) are " +
            "forbidden. Sending a workout payload off the phone is gated on a blocking privacy " +
            "review; see documentation/feature-specs/wear-phase-1-active-workout-tile.md.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        // Qualified chains nest, so only the outermost node is the whole reference. Imports belong
        // to ForbiddenImport; reporting them here too would double up on one line.
        if (expression.parent is KtDotQualifiedExpression) return
        if (expression.parent is KtImportDirective) return
        reportIfForbidden(expression)
    }

    override fun visitUserType(type: KtUserType) {
        super.visitUserType(type)
        if (type.parent is KtUserType) return
        reportIfForbidden(type)
    }

    private fun reportIfForbidden(element: KtElement) {
        val reference = element.text.replace(WHITESPACE, "")
        if (!reference.startsWith("$FORBIDDEN_PACKAGE.")) return
        report(
            CodeSmell(
                issue,
                Entity.from(element),
                "`$FORBIDDEN_PACKAGE` is referenced here without an import, which the " +
                    "ForbiddenImport gate cannot see. Sending any workout payload over the " +
                    "Wearable Data Layer is blocked on a privacy review that has not happened.",
            ),
        )
    }

    private companion object {
        const val FORBIDDEN_PACKAGE = "com.google.android.gms.wearable"
        val WHITESPACE = Regex("\\s+")
    }
}
