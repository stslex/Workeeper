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
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * The half of the Wear transport privacy gate that `ForbiddenImport` cannot reach: every way of
 * naming `com.google.android.gms.wearable` that carries no import directive.
 *
 * GUARD: match on PSI names, never on `element.text`. Source text carries comments and formatting
 * that a reader will not expect to matter, and a raw-text prefix is defeated by legal spellings.
 * GUARD: the package directive is a spelling too — a file declaring the forbidden package reaches
 * the API with bare identifiers that no visitor here can see.
 *
 * Deliberately AST-only, which is also what keeps this rule's own string-literal test fixtures from
 * flagging when detekt runs over `lint-rules`.
 *
 * See documentation/lint-rules.md § `WearDataLayerApiRule` for the derivation, the spellings this
 * covers, and the two it does not.
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

    override fun visitPackageDirective(directive: KtPackageDirective) {
        super.visitPackageDirective(directive)
        reportIfForbidden(directive, directive.qualifiedName)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        // Qualified chains nest, so only the outermost node is the whole reference.
        if (expression.parent is KtDotQualifiedExpression) return
        if (expression.isInsideDirective()) return
        reportIfForbidden(expression, expression.namePathOrNull())
    }

    override fun visitUserType(type: KtUserType) {
        super.visitUserType(type)
        if (type.parent is KtUserType) return
        reportIfForbidden(type, type.namePath())
    }

    /**
     * Import and package directives are reported by their own owners — `ForbiddenImport` and
     * [visitPackageDirective] — so the expression inside them must not report a second time.
     */
    private fun KtElement.isInsideDirective(): Boolean =
        getStrictParentOfType<KtImportDirective>() != null ||
            getStrictParentOfType<KtPackageDirective>() != null

    /**
     * The dotted identifier path this expression starts with, built from referenced names so that
     * comments and whitespace inside the chain cannot change the answer. `null` when the chain does
     * not start with a plain name path.
     */
    private fun KtExpression.namePathOrNull(): String? = when (this) {
        is KtNameReferenceExpression -> getReferencedName()
        is KtDotQualifiedExpression -> {
            val receiver = receiverExpression.namePathOrNull()
            val selector = (selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
            when {
                receiver == null -> null
                // A call ends the name path; the receiver is still the qualified name it was called on.
                selector == null -> receiver
                else -> "$receiver.$selector"
            }
        }

        else -> null
    }

    /** The type's qualified name, assembled from its qualifier chain rather than its text. */
    private fun KtUserType.namePath(): String = generateSequence(this) { it.qualifier }
        .mapNotNull { it.referencedName }
        .toList()
        .asReversed()
        .joinToString(separator = ".")

    private fun reportIfForbidden(element: KtElement, namePath: String?) {
        val path = namePath ?: return
        if (path != FORBIDDEN_PACKAGE && !path.startsWith("$FORBIDDEN_PACKAGE.")) return
        report(
            CodeSmell(
                issue,
                Entity.from(element),
                "`$FORBIDDEN_PACKAGE` is reached here without an import, which the " +
                    "ForbiddenImport gate cannot see. Sending any workout payload over the " +
                    "Wearable Data Layer is blocked on a privacy review that has not happened.",
            ),
        )
    }

    private companion object {
        const val FORBIDDEN_PACKAGE = "com.google.android.gms.wearable"
    }
}
