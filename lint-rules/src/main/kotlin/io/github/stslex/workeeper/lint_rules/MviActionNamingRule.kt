package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass

/**
 * Rule to enforce proper naming conventions for MVI Actions
 */
class MviActionNamingRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "MVI Actions should follow naming conventions",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (className.endsWith("Action") && klass.isInMviModule()) {
            if (!klass.hasModifier(KtTokens.SEALED_KEYWORD) && !klass.isInterface()) {
                report(
                    CodeSmell(
                        issue, Entity.from(klass),
                        "Action class '$className' should be sealed class or interface",
                    ),
                )
            }
        }
    }

    private fun KtClass.isInMviModule(): Boolean {
        return containingKtFile.packageFqName.asString().contains("mvi") ||
                containingKtFile.virtualFilePath.contains("/mvi/")
    }
}