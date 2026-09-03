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
 * The navigation route arg (`Screen` / `Screen.X`) may be injected only into a Store's primary
 * constructor, never into another feature-graph node. See documentation/lint-rules.md.
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
        // Only a Store's primary constructor may take the arg; `*HandlerStoreImpl` is not a Store.
        val isStoreImpl = ScopedClassNames.isStoreImpl(className)

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

        // Secondary constructors are never an injection point — flagged even on a StoreImpl.
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
     * True when the declared type text is `Screen` or a nested route type — PSI has no type
     * resolution, so an unrelated `ScreenSize` must not match.
     */
    private fun KtParameter.referencesScreenType(): Boolean {
        val declared = typeReference?.text?.substringBefore('<')?.trim() ?: return false
        val simpleChain = declared.substringAfterLast(SCREEN_PACKAGE_SEPARATOR_HINT).trim()
        return simpleChain == SCREEN || simpleChain.startsWith("$SCREEN.")
    }

    private companion object {

        const val SCREEN = "Screen"

        /** Strips the `...navigation.` qualifier, leaving the `Screen`-rooted chain intact. */
        const val SCREEN_PACKAGE_SEPARATOR_HINT = "navigation."

        val INJECT_ANNOTATIONS = setOf("Inject", "AssistedInject")
    }
}
