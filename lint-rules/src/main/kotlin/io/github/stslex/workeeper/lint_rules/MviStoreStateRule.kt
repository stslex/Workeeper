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

/** A nested `State` in a `*Store` must be a data class implementing `Store.State`. */
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

        if (klass.containingKtFile.virtualFilePath.contains("/test/")) {
            return
        }

        if (className != "State") {
            return
        }

        val parentClass = klass.parent?.parent as? KtClass
        if (parentClass == null || !parentClass.name.orEmpty().endsWith("Store")) {
            return
        }

        if (!klass.hasModifier(KtTokens.DATA_KEYWORD)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "State class in '${parentClass.name}' must be a data class",
                ),
            )
        }

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
