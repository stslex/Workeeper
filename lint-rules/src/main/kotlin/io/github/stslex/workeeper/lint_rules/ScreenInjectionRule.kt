// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Graph-extension arc guard: the navigation route arg (`Screen` / `Screen.X`) may be injected ONLY into a
 * Store's primary constructor.
 *
 * The arc moves each feature's route arg from an `@Assisted` store parameter to a `@Provides` bound
 * instance on the contributed `@GraphExtension.Factory` (shape B):
 *
 * ```
 * @ContributesTo(AppScope::class)
 * @GraphExtension.Factory
 * fun interface Factory {
 *     fun createImageViewerGraph(@Provides screen: Screen.ExerciseImage): ImageViewerGraph
 * }
 * ```
 *
 * That trade is deliberate — it deletes all assisted machinery from the feature — but it costs a
 * mechanical guarantee. Under `@AssistedInject` the route arg could reach NOTHING except the store
 * constructor, enforced by the compiler. As a bound instance it becomes an ORDINARY binding in the
 * feature-scope graph, so any `@Inject` node in that scope (a Handler, an Interactor, a Mapper) can
 * declare `Screen.X` as a constructor dependency and read navigation state straight out of DI —
 * bypassing the Store and the unidirectional state flow the MVI rules exist to protect. Metro validates
 * nothing at the call site (the same false-green class [ContributesBindingScopeRule] and
 * [ContributesToScopeRule] were written for), so the guarantee has to be re-established by a rule.
 *
 * This rule fails any `@Inject` / `@AssistedInject` class that takes a `Screen` type as a constructor
 * parameter unless the class is a Store implementation (`*StoreImpl`) and the parameter sits in its
 * PRIMARY constructor — the one place the arg legitimately enters, to seed `initialState`.
 *
 * Deliberately NOT flagged:
 *  - `@GraphExtension.Factory` / `@DependencyGraph.Factory` creator functions — these are interfaces, not
 *    injected classes, and are the legitimate entry point for the arg;
 *  - `Feature` / `FeatureAssisted` composition seams — `processor(screen)` is a function parameter on a
 *    non-injected object, not DI;
 *  - ordinary (non-`@Inject`) classes and functions that take a `Screen`, e.g. UI mappers and navigation
 *    helpers — they receive it explicitly from a caller rather than resolving it from a graph.
 */
class ScreenInjectionRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "A Screen route arg may only be injected into a Store's primary constructor",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (klass.containingKtFile.virtualFilePath.contains("/test/")) return
        if (klass.isInterface()) return

        // Only DI-constructed classes are in scope: the hazard is resolving the arg FROM THE GRAPH.
        if (klass.isInjected().not()) return

        val className = klass.name ?: return
        // The Store's primary constructor is the one legitimate sink — it seeds initialState there.
        val isStoreImpl = className.endsWith(STORE_IMPL_SUFFIX)

        klass.primaryConstructor
            ?.valueParameters
            .orEmpty()
            .filter { it.referencesScreenType() }
            .forEach { parameter ->
                if (isStoreImpl) return@forEach
                report(
                    CodeSmell(
                        issue,
                        Entity.from(parameter),
                        "'$className' injects the navigation route arg " +
                            "'${parameter.typeReference?.text}' from the feature graph. Since the graph-" +
                            "extension arc binds the route arg as a @Provides instance, ANY node in the " +
                            "feature scope can resolve it — reading navigation state out of DI instead of " +
                            "through the Store. Inject it only into the Store's primary constructor and " +
                            "let it reach this class as Store state.",
                    ),
                )
            }

        // Secondary constructors are never a Metro injection point for the arg; flag them everywhere,
        // including on a StoreImpl, so the arg cannot sneak in through a non-primary path.
        klass.secondaryConstructors
            .flatMap { it.valueParameters }
            .filter { it.referencesScreenType() }
            .forEach { parameter ->
                report(
                    CodeSmell(
                        issue,
                        Entity.from(parameter),
                        "'$className' takes the navigation route arg '${parameter.typeReference?.text}' " +
                            "in a SECONDARY constructor. The route arg enters only through the Store's " +
                            "PRIMARY constructor.",
                    ),
                )
            }
    }

    /** True when the class or its primary constructor carries a Metro injection annotation. */
    private fun KtClass.isInjected(): Boolean {
        val onClass = annotationEntries.any { it.shortName?.asString() in INJECT_ANNOTATIONS }
        val onConstructor = primaryConstructor
            ?.annotationEntries
            ?.any { it.shortName?.asString() in INJECT_ANNOTATIONS }
            ?: false
        return onClass || onConstructor
    }

    /**
     * True when this parameter's declared type is the navigation [Screen] sealed interface or one of its
     * nested route types. PSI has no type resolution, so the declared text is the discriminator:
     * `Screen`, `Screen.ExerciseImage`, `Screen.BottomBar.Home`, or a fully-qualified
     * `...core.ui.navigation.Screen.Training` all match, while an unrelated `ScreenSize` does not.
     */
    private fun KtParameter.referencesScreenType(): Boolean {
        val declared = typeReference?.text?.substringBefore('<')?.trim() ?: return false
        val simpleChain = declared.substringAfterLast(SCREEN_PACKAGE_SEPARATOR_HINT).trim()
        return simpleChain == SCREEN || simpleChain.startsWith("$SCREEN.")
    }

    private companion object {

        const val SCREEN = "Screen"
        const val STORE_IMPL_SUFFIX = "StoreImpl"

        /**
         * Strips a package qualifier so `io.github...navigation.Screen.Training` reduces to
         * `Screen.Training`. The navigation package always ends in `.navigation.`, so cutting on it
         * leaves the `Screen`-rooted chain intact (a bare `Screen.X` has no match and is unchanged).
         */
        const val SCREEN_PACKAGE_SEPARATOR_HINT = "navigation."

        val INJECT_ANNOTATIONS = setOf("Inject", "AssistedInject")
    }
}
