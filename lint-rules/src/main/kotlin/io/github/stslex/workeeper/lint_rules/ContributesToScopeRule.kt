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
 * App-Scope Collapse Step 3 (Phase PF commit 0) false-green guard for Metro `@BindingContainer`
 * contributions — the provides-factory twin of [ContributesBindingScopeRule].
 *
 * The provides-factory mechanic (`@Provides` inside a public `@BindingContainer @ContributesTo(scope)`)
 * lets Metro own factory bindings cross-module. Aggregation is decided ENTIRELY by the container's
 * `@ContributesTo` scope argument — and, like `@ContributesBinding`, Metro validates nothing at the call
 * site: `@ContributesTo(KClass<*>)` accepts ANY class. A container contributed to the WRONG scope compiles
 * GREEN and silently fails to aggregate into the app-scope `AppGraph`
 * (`@DependencyGraph(scope = AppScope::class)`); its `@Provides` bindings are simply absent at runtime.
 *
 * This was verified empirically on Metro 1.1.1 (PF.0 gate): a `@BindingContainer @ContributesTo` to a
 * feature scope OR to Metro's built-in `dev.zacsweers.metro.AppScope` (a DIFFERENT class from the project
 * token, same simple name) both compile with zero diagnostic when no reader forces resolution — a
 * silent-absence false-green. (The duplicate-binding failure mode IS compile-caught — `[Metro/DuplicateBinding]`
 * — so it needs no rule; only the scope-aggregation modes are silent.)
 *
 * This rule fails any `@BindingContainer` whose `@ContributesTo` scope argument is not the project `AppScope`
 * (`io.github.stslex.workeeper.core.core.di.AppScope`). Three failure modes are caught:
 *  - a `@BindingContainer` with NO `@ContributesTo` at all (orphan — never aggregated, silently inert);
 *  - a `@ContributesTo` whose scope simple-name is not `AppScope` (a feature scope, a typo, another marker); and
 *  - `AppScope` imported from `dev.zacsweers.metro` — Metro's built-in app scope (wrong class, same simple
 *    name). PSI has no type resolution, so the import origin is the discriminator.
 *
 * The rule only applies to `@BindingContainer`; every other declaration is ignored. It deliberately does
 * NOT check `@SingleIn` placement on the inner `@Provides` — lifetime (scoped vs factory-per-request) is a
 * separate, non-aggregation concern; only the container's `@ContributesTo` decides whether the app graph
 * ever sees the bindings.
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

        // Simple name is AppScope — but reject Metro's BUILT-IN AppScope (a different class from the
        // project token the AppGraph is scoped to). PSI can't resolve the type, so check the import.
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

    /**
     * Simple name of the first scope class-literal argument (`@ContributesTo(AppScope::class)` →
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

    /** True when this declaration's file explicitly imports Metro's built-in `dev.zacsweers.metro.AppScope`. */
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
