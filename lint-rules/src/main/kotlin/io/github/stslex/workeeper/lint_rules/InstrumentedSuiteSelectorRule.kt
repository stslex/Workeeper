// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotated
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
 * Both were live in this repo when the rule was written (2026-08-16). `core/ui/kit` and
 * `feature/app-dialogs/impl` had the first, `core/ui/mvi` the second — and the second had never
 * been noticed, because the only visible symptom of a test that never runs is a number nobody
 * had reason to distrust.
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

        if (!function.hasAnnotation(TEST)) return

        val bound = function.containingKtFile.suiteNamesInScope()
        if (function.hasSuiteAnnotation(bound)) return
        if (function.containingClassOrObject?.hasSuiteAnnotation(bound) == true) return

        val owner = function.containingClassOrObject?.name ?: function.containingKtFile.name
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
     */
    private fun KtFile.suiteNamesInScope(): Set<String> = importDirectives
        .mapNotNull { directive ->
            val imported = directive.importedFqName?.asString() ?: return@mapNotNull null
            if (imported !in CANONICAL_SUITE_ANNOTATIONS) return@mapNotNull null
            directive.aliasName ?: imported.substringAfterLast('.')
        }
        .toSet()

    private fun KtAnnotated.hasSuiteAnnotation(boundNames: Set<String>): Boolean =
        annotationEntries.any { entry ->
            val referenced = entry.typeReference?.text?.trim() ?: return@any false
            referenced in CANONICAL_SUITE_ANNOTATIONS || referenced in boundNames
        }

    private fun KtAnnotated.hasAnnotation(shortName: String): Boolean =
        annotationEntries.any { it.shortName?.asString() == shortName }

    private companion object {
        const val TEST = "Test"
        const val ANNOTATIONS_PACKAGE = "io.github.stslex.workeeper.core.ui.test.annotations"
        val CANONICAL_SUITE_ANNOTATIONS = setOf(
            "$ANNOTATIONS_PACKAGE.Smoke",
            "$ANNOTATIONS_PACKAGE.Regression",
        )
    }
}
