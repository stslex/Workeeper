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
 * Every instrumented `@Test` must be reachable by `ui_tests.yml`'s suite selector: it — or its
 * enclosing class — must carry `@Smoke` or `@Regression`, resolved to
 * `io.github.stslex.workeeper.core.ui.test.annotations`.
 *
 * This rule is one half of a two-part gate. The other half is
 * `:<module>:verifyInstrumentedSuiteClasspath`, which asserts the annotation is on the test
 * APK's runtime classpath. Neither half subsumes the other, because the underlying defect has
 * two signs and each check catches only one of them:
 *
 * - **Over-inclusion.** androidx.test's `TestRequestBuilder` SILENTLY DROPS an
 *   `-e annotation <fqn>` filter when the annotation class cannot be loaded in the test APK.
 *   The whole suite then runs under *every* selector, and the run reports nothing unusual.
 * - **Under-inclusion (a vanish).** Where the annotation *does* load, the filter applies — and
 *   an unannotated test is excluded from `Smoke` and `Regression` alike. It runs in no suite at
 *   all, and again nothing is reported: a selector that matches nothing is green, vacuously.
 *
 * Both signs are reachable in this repo and both have been observed; the measured cases and their
 * arithmetic live in `documentation/feature-specs/kmp-phase-0-instrumented-filter.md` → "One hole,
 * two opposite signs".
 *
 * **Why the import is load-bearing.** The rule runs without type resolution, so it matches
 * annotations by name. A locally declared `annotation class Smoke` would satisfy a naive
 * name check while leaving the real filter unsatisfiable — the same false green in a new
 * costume. Coverage is therefore credited only when the name is bound to the canonical package
 * by an import (alias included) or written fully qualified.
 *
 * **Scope is the task, not the rule.** The rule is inactive in `lint-rules/detekt.yml` and
 * active only in `lint-rules/detekt-androidtest-suite.yml`, which the convention plugin points
 * at `src/androidTest` alone. `src/test` unit tests take no suite annotation — they are not
 * selected by an instrumentation filter — so a repo-wide activation would be wrong, not merely
 * noisy.
 *
 * `@Ignore`d tests are still required to carry a suite annotation. An ignored test is inert
 * under either selector, so the requirement costs nothing; exempting it would add a second way
 * for a test to be un-selectable and a second thing for a reader to verify.
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

        // An INHERITABLE declaring class is rejected outright, whatever it is annotated with.
        // JUnit 4 runs an inherited @Test under the CONCRETE subclass, and selectability is then
        // decided by that subclass's annotations — which this rule cannot see without type
        // resolution. Both directions of that are broken and neither is visible here: a class-level
        // annotation on the base does not travel (`@Smoke`/`@Regression` are not `@Inherited`), and
        // a concrete subclass that declares no `@Test` of its own is never visited by this
        // function-level visitor at all. Refusing the shape is what makes the blind spot
        // unreachable; the alternative is a rule that reports "fine" about a question it did not
        // ask. Declare instrumented tests in the concrete class that runs them.
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
     * Simple names bound to a canonical suite annotation in this file — the import's alias when
     * it has one, else the annotation's own name. A file with no such import binds nothing, so
     * a bare `@Smoke` in it refers to something else and credits no coverage.
     *
     * A star import over the annotations package binds every canonical name. That branch is
     * load-bearing rather than defensive: `detekt.yml`'s `WildcardImport` rule excludes every
     * androidTest path, and ktlint's `NoWildcardImports` never reaches that source set either
     * (the plain `detekt` task cannot see it, and `detekt-androidtest-suite.yml` activates only
     * this rule). Wildcard imports are therefore permitted in exactly the source set this rule
     * polices, so failing to bind them would report correctly-annotated tests as unselectable —
     * a false positive that reds the gate on good code. `isAllUnder` is checked before the
     * exact-FQN comparison because a star import's `importedFqName` is the PACKAGE, not a class,
     * and would otherwise silently fall through to "binds nothing".
     */
    private fun KtFile.suiteNamesInScope(): Set<String> = importDirectives
        .flatMap { directive ->
            val imported = directive.importedFqName?.asString() ?: return@flatMap emptyList()
            when {
                // `import <annotations package>.*` — an alias is not expressible on a star
                // import, so each annotation is bound under its own simple name.
                directive.isAllUnder && imported == ANNOTATIONS_PACKAGE -> CANONICAL_SIMPLE_NAMES
                // A star over any other package binds none of ours.
                directive.isAllUnder -> emptyList()
                imported in CANONICAL_SUITE_ANNOTATIONS ->
                    listOf(directive.aliasName ?: imported.substringAfterLast('.'))

                else -> emptyList()
            }
        }
        .toSet()

    private fun KtAnnotated.hasSuiteAnnotation(boundNames: Set<String>): Boolean =
        hasAnnotationIn(boundNames, CANONICAL_SUITE_ANNOTATIONS)

    /**
     * A class another class can extend. Kotlin classes are final by default, so only these three
     * modifiers can put a `@Test` into a superclass position.
     */
    private fun KtClassOrObject.isInheritable(): Boolean =
        hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
            hasModifier(KtTokens.SEALED_KEYWORD) ||
            hasModifier(KtTokens.OPEN_KEYWORD)

    /**
     * Simple names that denote a JUnit `@Test` in this file.
     *
     * Detection here must be at least as permissive as JUnit's own resolution, because the two
     * directions are not symmetric. Missing a test annotation makes an unselectable test INVISIBLE
     * to this rule — it passes, runs in no suite, and nothing reports it, which is precisely the
     * defect the rule exists to catch. Over-matching only ever asks for a suite annotation on
     * something that may not need one, which is loud and trivially corrected.
     *
     * So the bare name is always included — it is the shape every test in this repo uses, and it
     * holds whether or not the import is analysable — and aliases and star imports of the canonical
     * JUnit annotations are added on top. `import org.junit.Test as InstrumentedTest` compiles to a
     * JUnit test like any other.
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
     * True when some annotation on this element is one of [canonicalFqNames], either by its simple
     * name resolving through [boundNames] or by being written fully qualified.
     *
     * BOTH spellings are read, and that is the point. `shortName` normalises the identifier — it
     * strips the backticks off an escaped `` @`Test` `` — while the raw `typeReference.text` does
     * not; conversely only the raw text carries a fully-qualified `@org.junit.Test`. Reading either
     * one alone leaves a spelling the compiler accepts and this rule cannot see, which for a test
     * annotation means an unselectable test passing unreported.
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

        /** JUnit 4 and 5. Instrumented tests are JUnit 4; 5 is listed so the rule cannot be
         * evaded by a runner change, and over-matching is the safe direction. */
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
