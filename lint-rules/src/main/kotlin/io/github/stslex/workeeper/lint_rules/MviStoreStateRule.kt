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
 * Rule to ensure Store.State is properly defined
 *
 * Store.State should:
 * - Be a data class
 * - Implement Store.State interface
 * - Have immutable properties
 */
class MviStoreStateRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Store.State must be a data class implementing Store.State interface",
        debt = Debt.TEN_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        // Skip test classes
        if (klass.containingKtFile.virtualFilePath.contains("/test/")) {
            return
        }

        // Check if this is a State class in a Store
        if (className != "State") {
            return
        }

        // Check if parent is a Store
        val parentClass = klass.parent?.parent as? KtClass
        if (parentClass == null || !parentClass.name.orEmpty().endsWith("Store")) {
            return
        }

        // Check if it's a data class
        if (!klass.hasModifier(KtTokens.DATA_KEYWORD)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "State class in '${parentClass.name}' must be a data class",
                ),
            )
        }

        // Check if it implements Store.State
        val implementsStoreState = klass.superTypeListEntries.any {
            it.text.contains("Store.State")
        }

        if (!implementsStoreState) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "State class in '${parentClass.name}' must implement Store.State interface",
                ),
            )
        }
    }
}
