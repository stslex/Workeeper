package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass

/**
 * Rule to ensure MVI Handlers use constructor injection
 *
 * Handlers should:
 * - Have @Inject annotation on primary constructor
 * - Implement Handler<ActionType> interface
 * - Not have empty constructors
 */
class MviHandlerConstructorRule(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "MVI Handlers must use proper constructor injection",
        Debt.TEN_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        // Skip test classes
        if (klass.containingKtFile.virtualFilePath.contains("/test/")) {
            return
        }

        // Check if this is a Handler class
        if (!className.endsWith("Handler") || klass.isInterface()) {
            return
        }

        // Check if it implements Handler interface
        val implementsHandler = klass.superTypeListEntries.any {
            it.text.contains("Handler")
        }

        if (!implementsHandler) {
            return
        }

        // Check primary constructor
        val primaryConstructor = klass.primaryConstructor
        if (primaryConstructor == null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "Handler class '$className' must have a primary constructor with @Inject"
                )
            )
            return
        }

        // Check for @Inject annotation
        val hasInject = primaryConstructor.annotationEntries.any {
            it.shortName?.asString() == "Inject"
        }

        if (!hasInject) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(primaryConstructor),
                    "Handler '$className' constructor must have @Inject annotation"
                )
            )
        }

        // Check if constructor has parameters
        val hasParameters = primaryConstructor.valueParameters.isNotEmpty()
        if (!hasParameters) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(primaryConstructor),
                    "Handler '$className' should have dependencies injected via constructor"
                )
            )
        }
    }
}
