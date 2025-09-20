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
class MviActionNamingRule(config: Config = Config.Companion.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "MVI Actions should follow naming conventions",
        Debt.Companion.TWENTY_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (className.endsWith("Action") && klass.isInMviModule()) {
            // Actions should be sealed classes or interfaces
            if (!klass.hasModifier(KtTokens.SEALED_KEYWORD) && !klass.isInterface()) {
                report(
                    CodeSmell(
                        issue, Entity.Companion.from(klass),
                        "Action class '$className' should be sealed class or interface"
                    )
                )
            }

            // Check nested action classes
            klass.declarations.filterIsInstance<KtClass>().forEach { nestedClass ->
                val nestedName = nestedClass.name ?: return@forEach
                if (!isValidActionName(nestedName)) {
                    report(
                        CodeSmell(
                            issue, Entity.Companion.from(nestedClass),
                            "Action '$nestedName' should use verb-noun pattern (e.g., ClickButton, LoadData)"
                        )
                    )
                }
            }
        }
    }

    private fun isValidActionName(name: String): Boolean {
        val validPatterns = listOf(
            "Click", "Load", "Save", "Delete", "Update", "Navigate",
            "Search", "Filter", "Sort", "Refresh", "Retry", "Cancel",
            "Show", "Hide", "Toggle", "Select", "Clear", "Reset"
        )
        return validPatterns.any { name.startsWith(it) }
    }

    private fun KtClass.isInMviModule(): Boolean {
        return containingKtFile.packageFqName.asString().contains("mvi") ||
                containingKtFile.virtualFilePath.contains("/mvi/")
    }
}