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
import org.jetbrains.kotlin.psi.KtClass

/**
 * Fails any `@ContributesBinding` not scoped to the PROJECT `AppScope` — a wrong scope compiles
 * green and is silently absent at runtime. See documentation/app-scope-collapse-execution-spec.md.
 */
class ContributesBindingScopeRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "@ContributesBinding must be scoped to the project AppScope so the app graph aggregates it",
        debt = Debt.TEN_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (klass.containingKtFile.virtualFilePath.contains("/test/")) return

        // `@ContributesBinding` is @Repeatable: every entry carries its own scope and is checked.
        val contributesEntries = klass.annotationEntries.filter {
            it.shortName?.asString() == CONTRIBUTES_BINDING
        }
        if (contributesEntries.isEmpty()) return

        val importsMetroAppScope = klass.importsMetroAppScope()
        contributesEntries.forEach { entry ->
            val violation = entry.scopeViolation(klass, importsMetroAppScope) ?: return@forEach
            report(violation)
        }
    }

    /**
     * The scope violation of a single `@ContributesBinding` entry, or null when it is correctly
     * scoped. [importsMetroAppScope] is a file-level fact, resolved once by the caller.
     */
    private fun KtAnnotationEntry.scopeViolation(
        klass: KtClass,
        importsMetroAppScope: Boolean,
    ): CodeSmell? {
        val scopeName = firstScopeArgumentSimpleName()
        return when {
            scopeName == null -> CodeSmell(
                issue,
                Entity.from(this),
                "@ContributesBinding on '${klass.name}' must declare an explicit scope argument " +
                    "of the project $APP_SCOPE.",
            )

            scopeName != APP_SCOPE -> CodeSmell(
                issue,
                Entity.from(this),
                "@ContributesBinding on '${klass.name}' is scoped to '$scopeName', not the project " +
                    "$APP_SCOPE — it will not aggregate into the app graph (silently absent at runtime).",
            )

            // Reject Metro's built-in AppScope: same simple name, different class from the project.
            importsMetroAppScope -> CodeSmell(
                issue,
                Entity.from(this),
                "@ContributesBinding on '${klass.name}' uses Metro's built-in " +
                    "$METRO_APP_SCOPE_FQN, not the project $APP_SCOPE — the app graph is scoped to " +
                    "the project token, so this contribution will not aggregate.",
            )

            else -> null
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

    /** True when this class explicitly imports Metro's built-in `dev.zacsweers.metro.AppScope`. */
    private fun KtClass.importsMetroAppScope(): Boolean =
        containingKtFile.importDirectives.any {
            it.importedFqName?.asString() == METRO_APP_SCOPE_FQN
        }

    private companion object {

        const val CONTRIBUTES_BINDING = "ContributesBinding"

        /** The project app-scope token the app-scope `AppGraph` is scoped to. */
        const val APP_SCOPE = "AppScope"

        /** Metro's built-in app scope — a DIFFERENT class from the project [APP_SCOPE]. */
        const val METRO_APP_SCOPE_FQN = "dev.zacsweers.metro.AppScope"
    }
}
