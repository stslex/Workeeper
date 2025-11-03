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
class HiltScopeRule(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Hilt scoped components should use proper scope annotations",
        Debt.TEN_MINS
    )

    private val viewModelScopedClasses = listOf("Handler", "Store", "Interactor", "Mapper")
    private val singletonClasses = listOf("Repository", "DataStore", "Database")

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        // Skip test classes and interfaces
        if (klass.containingKtFile.virtualFilePath.contains("/test/") || klass.isInterface()) {
            return
        }

        val annotations = klass.annotationEntries
        val hasViewModelScoped = annotations.any {
            it.shortName?.asString() == "ViewModelScoped"
        }
        val hasSingleton = annotations.any {
            it.shortName?.asString() == "Singleton"
        }
        val hasInject = klass.primaryConstructor?.annotationEntries?.any {
            it.shortName?.asString() == "Inject"
        } ?: false

        // Check if class should be ViewModel scoped
        val shouldBeViewModelScoped = viewModelScopedClasses.any { className.contains(it) }
        val shouldBeSingleton = singletonClasses.any { className.contains(it) }

        // If class has @Inject, it should have a scope annotation
        if (hasInject && !hasViewModelScoped && !hasSingleton) {
            if (shouldBeViewModelScoped || shouldBeSingleton) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(klass),
                        "Class '$className' with @Inject must have ${if (shouldBeViewModelScoped) "@ViewModelScoped" else "@Singleton"} annotation"
                    )
                )
            }
        }

        // Handlers and Stores must be @ViewModelScoped
        if ((className.endsWith("Handler") || className.endsWith("StoreImpl")) && hasInject) {
            if (!hasViewModelScoped) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(klass),
                        "${if (className.endsWith("Handler")) "Handler" else "Store"} class '$className' must use @ViewModelScoped annotation"
                    )
                )
            }

            // Should not be Singleton
            if (hasSingleton) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(klass),
                        "${if (className.endsWith("Handler")) "Handler" else "Store"} class '$className' should not be @Singleton - use @ViewModelScoped instead"
                    )
                )
            }
        }

        // Repositories should be @Singleton
        if (className.endsWith("Repository") && hasInject && !hasSingleton) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "Repository class '$className' should use @Singleton annotation"
                )
            )
        }
    }
}
