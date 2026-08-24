// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Requires every instrumented `@Test` to carry `@Smoke` or `@Regression` bound to the canonical
 * annotations package. See the kmp-phase-0 instrumented-filter spec.
 */
class InstrumentedSuiteSelectorRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "An instrumented @Test that carries neither @Smoke nor @Regression is " +
            "selected by no suite: where the annotation resolves, the filter excludes it from " +
            "both runs, and it silently never executes.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        if (!function.hasTestAnnotation(function.containingKtFile.testNamesInScope())) return

        val declaringClass = function.containingClassOrObject
        val owner = declaringClass?.name ?: function.containingKtFile.name

        // GUARD: reject an inheritable declaring class outright — `@Smoke`/`@Regression` are not
        // `@Inherited`, so selectability depends on subclass annotations this rule cannot see.
        if (declaringClass != null && declaringClass.isInheritable()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(function),
                    "`${function.name}` is an instrumented @Test declared in the inheritable " +
                        "class `$owner`. JUnit runs an inherited test under its concrete " +
                        "subclass, and `@Smoke`/`@Regression` are not `@Inherited`, so whether it " +
                        "is selected depends on annotations this rule cannot see. Declare " +
                        "instrumented tests in the concrete class that runs them.",
                ),
            )
            return
        }

        val bound = function.containingKtFile.suiteNamesInScope()
        if (function.hasSuiteAnnotation(bound)) return
        if (declaringClass?.hasSuiteAnnotation(bound) == true) return

        report(
            CodeSmell(
                issue,
                Entity.from(function),
                "`${function.name}` in `$owner` is an instrumented @Test reachable by no " +
                    "suite selector. Add `@Smoke` or `@Regression` (on the test or its class), " +
                    "imported from `$ANNOTATIONS_PACKAGE` — which also requires " +
                    "`androidTestImplementation(project(\":core:ui:test-utils\"))`. Without " +
                    "both, `ui_tests.yml` either skips this test in every run or drops the " +
                    "filter entirely and runs the whole module in both runs.",
            ),
        )
    }

    /**
     * Simple names bound to a canonical suite annotation here (alias, own name, or star import).
     * Star imports are legal in androidTest, so `isAllUnder` is tested before the exact-FQN match.
     */
    private fun KtFile.suiteNamesInScope(): Set<String> = importDirectives
        .flatMap { directive ->
            val imported = directive.importedFqName?.asString() ?: return@flatMap emptyList()
            when {
                directive.isAllUnder && imported == ANNOTATIONS_PACKAGE -> CANONICAL_SIMPLE_NAMES
                directive.isAllUnder -> emptyList()
                imported in CANONICAL_SUITE_ANNOTATIONS ->
                    listOf(directive.aliasName ?: imported.substringAfterLast('.'))

                else -> emptyList()
            }
        }
        .toSet()

    private fun KtAnnotated.hasSuiteAnnotation(boundNames: Set<String>): Boolean =
        hasAnnotationIn(boundNames, CANONICAL_SUITE_ANNOTATIONS)

    /** A class another class can extend; Kotlin classes are final by default. */
    private fun KtClassOrObject.isInheritable(): Boolean =
        hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
            hasModifier(KtTokens.SEALED_KEYWORD) ||
            hasModifier(KtTokens.OPEN_KEYWORD)

    /**
     * Simple names that denote a JUnit `@Test` here. Over-matching is the safe direction: a name
     * this misses is an unselectable test the rule never sees.
     */
    private fun KtFile.testNamesInScope(): Set<String> = buildSet {
        add(TEST)
        importDirectives.forEach { directive ->
            val imported = directive.importedFqName?.asString() ?: return@forEach
            when {
                directive.isAllUnder && imported in JUNIT_TEST_PACKAGES -> add(TEST)
                imported in CANONICAL_TEST_ANNOTATIONS -> add(directive.aliasName ?: TEST)
            }
        }
    }

    private fun KtAnnotated.hasTestAnnotation(boundNames: Set<String>): Boolean =
        hasAnnotationIn(boundNames, CANONICAL_TEST_ANNOTATIONS)

    /**
     * True when an annotation here is one of [canonicalFqNames]. Both spellings are read:
     * `shortName` unescapes backticks, only the raw text carries a fully-qualified name.
     */
    private fun KtAnnotated.hasAnnotationIn(
        boundNames: Set<String>,
        canonicalFqNames: Set<String>,
    ): Boolean = annotationEntries.any { entry ->
        entry.shortName?.asString() in boundNames ||
            entry.typeReference?.text?.trim() in canonicalFqNames
    }

    private companion object {
        const val TEST = "Test"

        /** JUnit 4 and 5; over-matching is the safe direction. */
        val CANONICAL_TEST_ANNOTATIONS = setOf(
            "org.junit.Test",
            "org.junit.jupiter.api.Test",
        )
        val JUNIT_TEST_PACKAGES = CANONICAL_TEST_ANNOTATIONS
            .mapTo(mutableSetOf()) { it.substringBeforeLast('.') }

        const val ANNOTATIONS_PACKAGE = "io.github.stslex.workeeper.core.ui.test.annotations"
        val CANONICAL_SUITE_ANNOTATIONS = setOf(
            "$ANNOTATIONS_PACKAGE.Smoke",
            "$ANNOTATIONS_PACKAGE.Regression",
        )
        val CANONICAL_SIMPLE_NAMES = CANONICAL_SUITE_ANNOTATIONS.map { it.substringAfterLast('.') }
    }
}
