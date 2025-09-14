package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Rule to enforce proper naming for MVI Handlers
 */
class MviHandlerNamingRule(config: Config = Config.Companion.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "MVI Handlers should follow naming conventions",
        Debt.Companion.TWENTY_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (className.endsWith("Handler")) {
            // Handlers should be classes, not data classes
            if (klass.isData()) {
                report(
                    CodeSmell(
                        issue, Entity.Companion.from(klass),
                        "Handler class '$className' should not be a data class"
                    )
                )
            }

            // Check for proper handler method naming
            klass.declarations.filterIsInstance<KtNamedFunction>().forEach { function ->
                val functionName = function.name ?: return@forEach
                if (functionName.startsWith("handle") && !functionName.contains("Action")) {
                    report(
                        CodeSmell(
                            issue, Entity.Companion.from(function),
                            "Handler function '$functionName' should specify what it handles (e.g., handleClickAction)"
                        )
                    )
                }
            }
        }
    }
}