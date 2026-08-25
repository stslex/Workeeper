// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Fails any `@BindingContainer` not `@ContributesTo` the PROJECT `AppScope` (orphan included) — a
 * wrong scope compiles green and never aggregates. See app-scope-collapse-execution-spec.md.
 */
class ContributesToScopeRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "@BindingContainer must be @ContributesTo the project AppScope so the app graph aggregates it",
        debt = Debt.TEN_MINS,
    )

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)

        if (classOrObject.containingKtFile.virtualFilePath.contains("/test/")) return

        classOrObject.annotationEntries.firstOrNull {
            it.shortName?.asString() == BINDING_CONTAINER
        } ?: return

        val contributesEntry = classOrObject.annotationEntries.firstOrNull {
            it.shortName?.asString() == CONTRIBUTES_TO
        }

        if (contributesEntry == null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(classOrObject),
                    "@BindingContainer '${classOrObject.name}' has no @ContributesTo — it will not " +
                        "aggregate into the app graph (its @Provides bindings are silently absent at runtime). " +
                        "Add @ContributesTo($APP_SCOPE::class).",
                ),
            )
            return
        }

        val scopeName = contributesEntry.firstScopeArgumentSimpleName()
        if (scopeName == null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(classOrObject),
                    "@ContributesTo on @BindingContainer '${classOrObject.name}' must declare an explicit " +
                        "scope argument of the project $APP_SCOPE.",
                ),
            )
            return
        }

        if (scopeName != APP_SCOPE) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(classOrObject),
                    "@ContributesTo on @BindingContainer '${classOrObject.name}' is scoped to '$scopeName', " +
                        "not the project $APP_SCOPE — its @Provides bindings will not aggregate into the app " +
                        "graph (silently absent at runtime).",
                ),
            )
            return
        }

        // Reject Metro's built-in AppScope: same simple name, different class from the project.
        if (classOrObject.importsMetroAppScope()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(classOrObject),
                    "@ContributesTo on @BindingContainer '${classOrObject.name}' uses Metro's built-in " +
                        "$METRO_APP_SCOPE_FQN, not the project $APP_SCOPE — the app graph is scoped to the " +
                        "project token, so this contribution will not aggregate.",
                ),
            )
        }
    }

    /** Simple name of the first scope class-literal argument, or null when absent. */
    private fun KtAnnotationEntry.firstScopeArgumentSimpleName(): String? {
        val argument = valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        return argument.text
            .substringBefore("::")
            .substringAfterLast('.')
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /** True when this file imports Metro's built-in `dev.zacsweers.metro.AppScope`. */
    private fun KtClassOrObject.importsMetroAppScope(): Boolean =
        containingKtFile.importDirectives.any {
            it.importedFqName?.asString() == METRO_APP_SCOPE_FQN
        }

    private companion object {

        const val BINDING_CONTAINER = "BindingContainer"
        const val CONTRIBUTES_TO = "ContributesTo"

        /** The project app-scope token the app-scope `AppGraph` is scoped to. */
        const val APP_SCOPE = "AppScope"

        /** Metro's built-in app scope — a DIFFERENT class from the project [APP_SCOPE]. */
        const val METRO_APP_SCOPE_FQN = "dev.zacsweers.metro.AppScope"
    }
}
