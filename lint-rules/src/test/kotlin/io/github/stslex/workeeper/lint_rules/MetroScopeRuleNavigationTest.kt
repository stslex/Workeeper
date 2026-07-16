// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the navigation-architecture-specific scope expectations of [MetroScopeRule]
 * after the lifecycle-safe navigation refactor.
 *
 * The rule walks `@Inject`-constructor classes whose name matches a dependency bucket
 * ([ScopeClassType]) and requires a Metro `@SingleIn(<Scope>::class)`. A `*Handler` additionally
 * must not be `@SingleIn(AppScope)`.
 *
 * The navigation architecture introduces classes that must NOT be flagged:
 *   - `NavigatorEventBus` — singleton command bus; its `Bus` suffix keeps its name out of every
 *     bucket predicate, so the rule never scope-checks it.
 *   - Feature `NavigationHandler`s — `@SingleIn(<Feature>Scope) @Inject Navigator` is the canonical
 *     shape after this refactor.
 *
 * These tests pin those invariants.
 */
internal class MetroScopeRuleNavigationTest {

    private val rule = MetroScopeRule()

    @Test
    fun `NavigatorEventBus is not flagged because the name does not match any predicate`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.navigation

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @SingleIn(AppScope::class)
            class NavigatorEventBus @Inject constructor()
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "NavigatorEventBus should pass MetroScopeRule — name carries 'Bus' suffix to dodge all predicates.",
        )
    }

    @Test
    fun `NavigationHandler with SingleIn feature scope passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @SingleIn(ExampleScope::class)
            internal class NavigationHandler @Inject constructor(
                private val navigator: Any,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "@SingleIn(<FeatureScope>) + @Inject NavigationHandler should pass")
    }

    @Test
    fun `NavigationHandler with javax Singleton triggers a finding`() {
        // @Singleton (javax.inject, retained for Metro includeJavax) is NOT a Metro scope — the graph
        // ignores it. A name-matched ctor-@Inject Handler with only @Singleton is silently unscoped.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import javax.inject.Inject
            import javax.inject.Singleton

            @Singleton
            internal class NavigationHandler @Inject constructor(
                private val navigator: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "NavigationHandler annotated @Singleton (not @SingleIn) must be flagged — its name matches a bucket.",
        )
    }

    @Test
    fun `NavigationHandler missing any scope triggers a finding`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import dev.zacsweers.metro.Inject

            internal class NavigationHandler @Inject constructor(
                private val navigator: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "NavigationHandler with @Inject but no @SingleIn must be flagged.",
        )
    }

    @Test
    fun `NavigatorReceiver as an interface is skipped`() {
        // The rule short-circuits on `klass.isInterface()` — interfaces have no scope by themselves.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.navigation

            interface NavigatorReceiver
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `NavigationModule has no ctor Inject and is skipped`() {
        // A class with no constructor @Inject short-circuits at the `hasInject` check and is not
        // scope-checked.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.di

            class NavigationModule
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }
}
