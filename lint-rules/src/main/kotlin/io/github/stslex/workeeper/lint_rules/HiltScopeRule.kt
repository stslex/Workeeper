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
 * Rule to check proper Hilt scope usage in MVI architecture
 *
 * This rule ensures that:
 * - Handler classes use @ViewModelScoped
 * - Store implementations use @ViewModelScoped
 * - Interactors use appropriate scoping
 * - Mappers and other ViewModel dependencies use @ViewModelScoped
 *
 * Metro path (KMP C.1 migration): a component managed by Metro instead of Hilt carries
 * `@SingleIn(<Scope>::class)` — Metro's `@ViewModelScoped`/`@Singleton` analogue. When that
 * annotation is present the class satisfies the scope requirement and the Hilt-annotation
 * policy is skipped for it. Classes with no `@SingleIn` are validated exactly as before, so
 * the 11 Hilt features are unaffected.
 *
 * Soundness guard on the Metro path: a `*Handler` is feature-scoped, never app-scoped, so
 * `@SingleIn(AppScope)` on a Handler is rejected (the scope ARGUMENT is read, not just the
 * annotation name). A Handler scoped to its own feature scope (`@SingleIn(<Feature>Scope)`)
 * passes.
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

        // Metro path (KMP C.1 M0+): a Metro-managed component carries @SingleIn(<Scope>::class),
        // Metro's equivalent of Hilt's @ViewModelScoped / @Singleton. When present it satisfies
        // the scope requirement for the scoped buckets, and — since a Metro graph resolves the
        // scope, not a Hilt component — the Hilt-annotation policy below does not apply. The Hilt
        // path is unaffected: no @SingleIn means byte-identical behaviour to before.
        // A Metro `Store` is intentionally UNSCOPED (retained by the Android ViewModelStore via
        // rememberMetroStoreProcessor), and it dodges this rule entirely via its class-level
        // @Inject (empty primary-constructor annotations → hasInject=false above), so no Store
        // branch is needed here.
        val singleInEntry = klass.annotationEntries.firstOrNull {
            it.shortName?.asString() == METRO_SCOPE_ANNOTATION
        }
        if (singleInEntry != null) {
            // Soundness guard: a Handler is feature-scoped, never app-scoped. `@SingleIn(AppScope)`
            // on a `*Handler` is a mis-scope (a per-screen Handler pinned to the app singleton
            // graph) — the name-only check accepted it. Read the scope argument and reject it.
            if (className.contains(HANDLER) && singleInEntry.referencesScope(APP_SCOPE)) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(klass),
                        "Handler '$className' must not be @SingleIn($APP_SCOPE) — a Handler is " +
                            "feature-scoped, not app-scoped. Scope it to its feature scope.",
                    ),
                )
            }
            return
        }

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

    /**
     * True when this `@SingleIn(...)` entry's scope argument references [scopeSimpleName]
     * (e.g. `@SingleIn(AppScope::class)` references `AppScope`). Matches on the class-literal's
     * simple name, so a fully-qualified `some.pkg.AppScope::class` is caught too, while a
     * feature scope like `ArchiveScope::class` is not.
     */
    private fun KtAnnotationEntry.referencesScope(scopeSimpleName: String): Boolean {
        val argument = valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        // Text is e.g. "AppScope::class" or "dev.zacsweers.metro.AppScope::class". Take the
        // referenced type name (strip "::class" and any package qualifier).
        val referencedType = argument.text
            .substringBefore("::")
            .substringAfterLast('.')
            .trim()
        return referencedType == scopeSimpleName
    }

    private companion object {

        /** Metro's scope annotation (`dev.zacsweers.metro.SingleIn`), the @ViewModelScoped analogue. */
        const val METRO_SCOPE_ANNOTATION = "SingleIn"

        /** Metro's app/singleton scope. A feature-scoped Handler must never be scoped to it. */
        const val APP_SCOPE = "AppScope"

        /** Name fragment identifying a Handler class (the feature-scoped-only bucket). */
        const val HANDLER = "Handler"
    }
}
