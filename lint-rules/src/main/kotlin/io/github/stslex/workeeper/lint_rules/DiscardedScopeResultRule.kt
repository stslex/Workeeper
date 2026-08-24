// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Flags a pure transform (`plus` / `map` / `copy` …) whose result is discarded as a statement
 * inside `apply { }` / `also { }`, which return their receiver rather than the lambda result.
 */
class DiscardedScopeResultRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Result of a pure transform is discarded inside an apply/also block",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val scopeName = expression.calleeExpression?.text ?: return
        if (scopeName != SCOPE_APPLY && scopeName != SCOPE_ALSO) return
        val body = expression.lambdaArguments
            .firstOrNull()
            ?.getLambdaExpression()
            ?.bodyExpression
            ?: return
        flattenStatements(body.statements)
            .filter { it.isDiscardedPureTransform() }
            .forEach { offender ->
                report(
                    CodeSmell(
                        issue,
                        Entity.from(offender),
                        "'${offender.text.take(MAX_SNIPPET)}' produces a new value that is " +
                            "discarded: `$scopeName` returns its receiver, not this result. " +
                            "Assign or return it (e.g. use buildList or reassignment).",
                    ),
                )
            }
    }

    /** Descends into control-flow branches (if/when) only, never into lambdas or call args. */
    private fun flattenStatements(statements: List<KtExpression>): List<KtExpression> =
        statements.flatMap { stmt ->
            when (stmt) {
                is KtIfExpression -> flattenStatements(
                    listOfNotNull(stmt.then, stmt.`else`).flatMap(::branchStatements),
                )

                is KtWhenExpression -> flattenStatements(
                    stmt.entries.mapNotNull { it.expression }.flatMap(::branchStatements),
                )

                else -> listOf(stmt)
            }
        }

    private fun branchStatements(expr: KtExpression?): List<KtExpression> = when (expr) {
        is KtBlockExpression -> expr.statements
        null -> emptyList()
        else -> listOf(expr)
    }

    private fun KtExpression.isDiscardedPureTransform(): Boolean = when (this) {
        is KtCallExpression -> calleeExpression?.text in PURE_TRANSFORMS
        is KtDotQualifiedExpression ->
            (selectorExpression as? KtCallExpression)?.calleeExpression?.text in PURE_TRANSFORMS

        is KtBinaryExpression ->
            operationToken == KtTokens.PLUS || operationToken == KtTokens.MINUS

        else -> false
    }

    private companion object {
        const val SCOPE_APPLY = "apply"
        const val SCOPE_ALSO = "also"
        const val MAX_SNIPPET = 40
        val PURE_TRANSFORMS = setOf(
            "plus", "minus", "plusElement", "minusElement",
            "map", "mapNotNull", "mapIndexed",
            "filter", "filterNot", "filterNotNull",
            "drop", "dropLast", "take", "takeLast",
            "sorted", "sortedBy", "sortedByDescending", "sortedWith",
            "reversed", "distinct", "distinctBy", "copy",
        )
    }
}
