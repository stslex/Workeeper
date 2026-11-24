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
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtUserType

class AndroidAppDepsFactoryLookupRule(config: Config = Config.empty) : Rule(config, RequiredFindings()) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Android production code must receive graph factories through injection.",
        Debt.TWENTY_MINS,
    )

    private var names = KotlinContractNames()

    override fun visitCondition(root: KtFile): Boolean = active

    override fun visitKtFile(file: KtFile) {
        names = KotlinContractNames(file, externalSymbols = setOf(APP_DEPS))
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return
        val factoryLookup = expression.typeArguments.any { argument ->
            val type = argument.typeReference?.typeElement as? KtUserType
            type != null && names.type(type).substringAfterLast('.') == "Factory"
        }
        if (!factoryLookup) return

        val receiver = (expression.parent as? KtQualifiedExpression)
            ?.takeIf { it.selectorExpression == expression }?.receiverExpression
        val receiverType = (receiver as? KtNameReferenceExpression)?.let(names::declaredType)
        val callable = names.reference(callee) { declaration ->
            val extension = (declaration as? KtNamedFunction)?.receiverTypeReference?.typeElement as? KtUserType
            receiver == null || extension != null && (receiverType == null || names.type(extension) == receiverType)
        }
        if (callable == APP_DEPS) {
            report(CodeSmell(issue, Entity.from(expression), "Android factory lookup via Context.appDeps is forbidden"))
        }
    }

    private companion object {
        const val APP_DEPS = "io.github.stslex.workeeper.core.ui.mvi.di.appDeps"
    }
}
