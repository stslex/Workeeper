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
 * MVI Handlers use constructor injection: `@Inject` on a non-empty primary constructor of a
 * `*Handler` implementing `Handler<A>`. See documentation/lint-rules.md.
 */
class MviHandlerConstructorRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "MVI Handlers must use proper constructor injection",
        debt = Debt.TEN_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (klass.containingKtFile.virtualFilePath.contains("/test/")) {
            return
        }

        if (!className.endsWith("Handler") || klass.isInterface()) {
            return
        }

        val implementsHandler = klass.superTypeListEntries.any {
            it.text.contains("Handler")
        }

        if (!implementsHandler) {
            return
        }

        val primaryConstructor = klass.primaryConstructor
        if (primaryConstructor == null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "Handler class '$className' must have a primary constructor with @Inject",
                ),
            )
            return
        }

        val hasInject = primaryConstructor.annotationEntries.any {
            it.shortName?.asString() == "Inject"
        }

        if (!hasInject && className != "NavigationHandler") {
            report(
                CodeSmell(
                    issue,
                    Entity.from(primaryConstructor),
                    "Handler '$className' constructor must have @Inject annotation",
                ),
            )
        }

        val hasParameters = primaryConstructor.valueParameters.isNotEmpty()
        if (!hasParameters) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(primaryConstructor),
                    "Handler '$className' should have dependencies injected via constructor",
                ),
            )
        }
    }
}
