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
 * Enforces Metro scope annotations on `@Inject`-annotated MVI dependencies.
 *
 * DI is 100% Metro (`dev.zacsweers.metro`). An injected component that participates in a feature graph
 * must declare its lifetime with `@SingleIn(<Scope>::class)` — the scope key that binds one instance to
 * one graph. Both Metro `@Inject` shapes count: the annotation on the primary constructor
 * (`class ClickHandler @Inject constructor(...)`) AND the class-level form
 * (`@Inject @SingleIn(X) class FooInteractorImpl(...)`), which is what most of the tree uses and which
 * also covers classes with no primary-constructor parens at all. `@AssistedInject` is deliberately NOT
 * treated as injection here: Metro forbids scoping an assisted type, so demanding a `@SingleIn` on one
 * would be wrong (`DataStoreProvider` is the live example). This rule walks those classes when their
 * name matches a known dependency bucket ([ScopedClassNames.isScopeChecked]) and requires:
 *
 * - **A scope must be declared.** A name-matched `@Inject` class with no `@SingleIn` is flagged: it either
 *   forgot the scope, or is using a non-Metro scope annotation (`javax.inject.@Singleton` still resolves
 *   because `javax.inject` is retained for Metro's `includeJavax()` qualifier interop, so a developer can
 *   still write `@Singleton` and be silently wrong under Metro — the graph does not honour it).
 * - **A Handler must not be app-scoped.** `@SingleIn(AppScope)` on a `*Handler` is a mis-scope (a
 *   per-screen Handler pinned to the process-lifetime app graph); the rule reads the scope ARGUMENT, not
 *   just the annotation name, and rejects it. A Handler scoped to its own feature scope
 *   (`@SingleIn(<Feature>Scope)`) passes.
 *
 * A Metro `Store` is intentionally UNSCOPED (retained by the Android `ViewModelStore` via
 * `rememberMetroStoreProcessor`), so a `*StoreImpl` is exempted BY NAME via
 * [ScopedClassNames.isStoreImpl] — an explicit exemption, not an accident of which `@Inject` shape it
 * happens to use. The `*HandlerStoreImpl` adapters are NOT covered by that exemption: they are ordinary
 * feature-scoped graph nodes and are scope-checked like any other `*Handler`.
 *
 * (Formerly `HiltScopeRule`: the Hilt-annotation branches — requiring `dagger.hilt.android.scopes.ViewModelScoped`
 * on Handler/Interactor/Mapper and `@HiltViewModel` on Store, plus the cross-bucket exclusivity loop — were
 * deleted once Hilt left every classpath, making those FQNs unresolvable. The retained checks all key off
 * annotations a developer can still write: `dev.zacsweers.metro.SingleIn` and `javax.inject.@Singleton`.)
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

        // Skip test classes and interfaces
        if (klass.containingKtFile.virtualFilePath.contains("/test/") || klass.isInterface()) {
            return
        }
        if (klass.isMetroInjected().not()) return

        // An MVI Store is intentionally UNSCOPED (the Android ViewModelStore retains it), so it is
        // exempted explicitly by name. `*HandlerStoreImpl` is NOT a Store in this sense and stays checked.
        if (ScopedClassNames.isStoreImpl(className)) return

        // Only name-matched dependency buckets are scope-checked (Repository / Handler / Interactor / …).
        // A name with no bucket (e.g. `NavigatorEventBus`, the `Bus` suffix dodges every predicate) is
        // intentionally unconstrained.
        if (ScopedClassNames.isScopeChecked(className).not()) return

        val singleInEntry = klass.annotationEntries.firstOrNull {
            it.shortName?.asString() == METRO_SCOPE_ANNOTATION
        }

        if (singleInEntry == null) {
            // No `@SingleIn` at all — the class either forgot its Metro scope or used a non-Metro one
            // (`javax.inject.@Singleton` resolves but the Metro graph ignores it). Flag it.
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

        // Soundness guard: a Handler is feature-scoped, never app-scoped. `@SingleIn(AppScope)` on a
        // `*Handler` is a mis-scope (a per-screen Handler pinned to the app singleton graph). Read the
        // scope argument and reject it.
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
     * True when the class is Metro-injected in either shape: `@Inject` on the class itself, or `@Inject`
     * on its primary constructor. Reading only the constructor would exempt the dominant shape in this
     * tree — every `*InteractorImpl` / `*HandlerStoreImpl` carries the annotation on the class, and some
     * have no primary-constructor parens at all, so `primaryConstructor` is null.
     *
     * `@AssistedInject` is intentionally excluded: Metro forbids scoping an assisted type.
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

        /** Metro's injection annotation (`dev.zacsweers.metro.Inject`), class- or constructor-level. */
        const val INJECT_ANNOTATION = "Inject"

        /** Metro's scope annotation (`dev.zacsweers.metro.SingleIn`). */
        const val METRO_SCOPE_ANNOTATION = "SingleIn"

        /** Metro's app/singleton scope. A feature-scoped Handler must never be scoped to it. */
        const val APP_SCOPE = "AppScope"

        /** Name fragment identifying a Handler class (the feature-scoped-only bucket). */
        const val HANDLER = "Handler"
    }
}
