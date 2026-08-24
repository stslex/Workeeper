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
 * Requires `@SingleIn(<Scope>::class)` on name-matched `@Inject` MVI dependencies and forbids
 * `@SingleIn(AppScope)` on a `*Handler`. See documentation/lint-rules.md.
 */
class MetroScopeRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Metro-scoped components must declare a correct @SingleIn scope",
        debt = Debt.TEN_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (klass.containingKtFile.virtualFilePath.contains("/test/") || klass.isInterface()) {
            return
        }
        if (klass.isMetroInjected().not()) return

        // An MVI Store is intentionally unscoped — the Android ViewModelStore retains it.
        if (ScopedClassNames.isStoreImpl(className)) return

        // Only name-matched dependency buckets are scope-checked; an unmatched name is free.
        if (ScopedClassNames.isScopeChecked(className).not()) return

        val singleInEntry = klass.annotationEntries.firstOrNull {
            it.shortName?.asString() == METRO_SCOPE_ANNOTATION
        }

        if (singleInEntry == null) {
            // No `@SingleIn`: forgotten, or a non-Metro `@Singleton` the Metro graph ignores.
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "Class '$className' must declare its Metro scope with @$METRO_SCOPE_ANNOTATION(<Scope>::class). " +
                        "A name-matched @Inject dependency with no @$METRO_SCOPE_ANNOTATION is unscoped (or is " +
                        "using a non-Metro scope like @Singleton, which the graph does not honour).",
                ),
            )
            return
        }

        // GUARD: a Handler is feature-scoped, never app-scoped — read the scope ARGUMENT.
        if (className.contains(HANDLER) && singleInEntry.referencesScope(APP_SCOPE)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "Handler '$className' must not be @$METRO_SCOPE_ANNOTATION($APP_SCOPE) — a Handler is " +
                        "feature-scoped, not app-scoped. Scope it to its feature scope.",
                ),
            )
        }
    }

    /**
     * True when Metro-injected in either shape: `@Inject` on the class or on its primary
     * constructor. `@AssistedInject` is excluded — Metro forbids scoping an assisted type.
     */
    private fun KtClass.isMetroInjected(): Boolean {
        val onClass = annotationEntries.any { it.shortName?.asString() == INJECT_ANNOTATION }
        val onPrimaryConstructor = primaryConstructor
            ?.annotationEntries
            ?.any { it.shortName?.asString() == INJECT_ANNOTATION }
            ?: false
        return onClass || onPrimaryConstructor
    }

    /**
     * True when this `@SingleIn(...)` argument references [scopeSimpleName], matched on the
     * class-literal's simple name so a fully-qualified scope is caught too.
     */
    private fun KtAnnotationEntry.referencesScope(scopeSimpleName: String): Boolean {
        val argument = valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        val referencedType = argument.text
            .substringBefore("::")
            .substringAfterLast('.')
            .trim()
        return referencedType == scopeSimpleName
    }

    private companion object {

        /** `dev.zacsweers.metro.Inject`, class- or constructor-level. */
        const val INJECT_ANNOTATION = "Inject"

        /** `dev.zacsweers.metro.SingleIn`. */
        const val METRO_SCOPE_ANNOTATION = "SingleIn"

        /** Metro's app/singleton scope; a Handler must never be scoped to it. */
        const val APP_SCOPE = "AppScope"

        const val HANDLER = "Handler"
    }
}
