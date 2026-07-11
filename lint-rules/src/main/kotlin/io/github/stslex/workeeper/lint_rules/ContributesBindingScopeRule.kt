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
 * App-Scope Collapse Step 3 false-green guard for Metro `@ContributesBinding`.
 *
 * `@ContributesBinding(scope = KClass<*>)` accepts ANY class as its scope argument — Metro validates
 * nothing at the call site. A binding annotated with the WRONG scope therefore COMPILES GREEN but
 * silently contributes to a different (or nonexistent) graph, so the app-scope `AppGraph`
 * (`@DependencyGraph(scope = AppScope::class)`) never aggregates it. At runtime the binding is simply
 * absent — a false-green with no compile signal, the same soundness class the pre-`d1cb7965`
 * `HiltScopeRule` gap had (it reads `@SingleIn` scope on Handlers only, and has zero
 * `@ContributesBinding` coverage).
 *
 * This rule fails any `@ContributesBinding` whose scope argument is not the PROJECT `AppScope`
 * (`io.github.stslex.workeeper.core.core.di.AppScope`). Two failure modes are caught:
 *  - a scope whose simple name is not `AppScope` at all (a feature scope, a typo, another marker); and
 *  - `AppScope` imported from `dev.zacsweers.metro` — Metro's BUILT-IN app scope, whose simple name is
 *    also `AppScope` but which is a DIFFERENT class from the project token the `AppGraph` is scoped to,
 *    so a contribution to it would not aggregate. PSI has no type resolution, so the import origin is
 *    the discriminator.
 *
 * The rule only applies to `@ContributesBinding`; every other contribution mechanism and all
 * non-contributing classes are ignored.
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

        val contributesEntry = klass.annotationEntries.firstOrNull {
            it.shortName?.asString() == CONTRIBUTES_BINDING
        } ?: return

        val scopeName = contributesEntry.firstScopeArgumentSimpleName()
        if (scopeName == null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "@ContributesBinding on '${klass.name}' must declare an explicit scope argument " +
                        "of the project $APP_SCOPE.",
                ),
            )
            return
        }

        if (scopeName != APP_SCOPE) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "@ContributesBinding on '${klass.name}' is scoped to '$scopeName', not the project " +
                        "$APP_SCOPE — it will not aggregate into the app graph (silently absent at runtime).",
                ),
            )
            return
        }

        // Simple name is AppScope — but reject Metro's BUILT-IN AppScope (a different class from the
        // project token the AppGraph is scoped to). PSI can't resolve the type, so check the import.
        if (klass.importsMetroAppScope()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "@ContributesBinding on '${klass.name}' uses Metro's built-in " +
                        "$METRO_APP_SCOPE_FQN, not the project $APP_SCOPE — the app graph is scoped to " +
                        "the project token, so this contribution will not aggregate.",
                ),
            )
        }
    }

    /**
     * Simple name of the first scope class-literal argument (`@ContributesBinding(AppScope::class)` →
     * `"AppScope"`; `some.pkg.WrongScope::class` → `"WrongScope"`), or null if no argument is present.
     */
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
