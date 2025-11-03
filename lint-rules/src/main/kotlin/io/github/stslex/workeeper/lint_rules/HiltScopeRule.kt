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
 * Rule to check proper Hilt scope usage in MVI architecture
 *
 * This rule ensures that:
 * - Handler classes use @ViewModelScoped
 * - Store implementations use @ViewModelScoped
 * - Interactors use appropriate scoping
 * - Mappers and other ViewModel dependencies use @ViewModelScoped
 */
class HiltScopeRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Hilt scoped components should use proper scope annotations",
        debt = Debt.TEN_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        // Skip test classes and interfaces
        if (klass.containingKtFile.virtualFilePath.contains("/test/") || klass.isInterface()) {
            return
        }
        val hasInject = klass.primaryConstructor?.annotationEntries?.any {
            it.shortName?.asString() == "Inject"
        } ?: false

        if (hasInject.not()) return
        val classType = ScopeClassType.getByName(className) ?: return

        val annotationNames = klass.annotationEntries.mapNotNull { it.shortName?.asString() }
        if (annotationNames.contains(classType.annotation).not()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "Class '$className' should use @${classType.annotation} annotation",
                ),
            )
        }

        val otherClasses = ScopeClassType.entries.filter { it != classType }
        otherClasses.forEach { otherClass ->
            if (annotationNames.contains(otherClass.annotation)) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(klass),
                        "Class '$className' should not use @${otherClass.annotation} annotation",
                    ),
                )
            }
        }
    }
}
