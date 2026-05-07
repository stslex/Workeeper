// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the navigation-architecture-specific scope expectations of `HiltScopeRule`
 * after the lifecycle-safe navigation refactor.
 *
 * The rule walks `@Inject`-annotated classes and applies a name-based scope policy
 * (`ScopeClassType`):
 *   - `Repository`, `DataStore`, `Database`, `Storage`, `StoreDispatchers` → `@Singleton`.
 *   - `Handler`, `Interactor`, `Mapper` → `@ViewModelScoped`.
 *   - `Store` → `@HiltViewModel`.
 *
 * The new navigation architecture introduces a few classes that must NOT be flagged
 * by these predicates:
 *   - `NavigatorEventBus` — singleton command bus, name carries the `Bus` suffix
 *     specifically to avoid matching any predicate.
 *   - Feature `NavigationHandler`s — `@ViewModelScoped @Inject Navigator` is the
 *     canonical shape after this refactor; the literal-name exemption in
 *     `MviHandlerConstructorRule` for `NavigationHandler` is back-compat only.
 *
 * These tests pin those invariants.
 */
internal class HiltScopeRuleNavigationTest {

    private val rule = HiltScopeRule()

    @Test
    fun `NavigatorEventBus is not flagged because the name does not match any predicate`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.navigation

            import javax.inject.Inject
            import javax.inject.Singleton

            @Singleton
            class NavigatorEventBus @Inject constructor()
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "NavigatorEventBus should pass HiltScopeRule — name carries 'Bus' suffix to dodge all predicates.",
        )
    }

    @Test
    fun `NavigationHandler with ViewModelScoped passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import dagger.hilt.android.scopes.ViewModelScoped
            import javax.inject.Inject

            @ViewModelScoped
            internal class NavigationHandler @Inject constructor(
                private val navigator: Any,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "ViewModelScoped + @Inject NavigationHandler should pass")
    }

    @Test
    fun `NavigationHandler with Singleton triggers a finding`() {
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
            "NavigationHandler annotated @Singleton must be flagged — its name matches the @ViewModelScoped predicate.",
        )
    }

    @Test
    fun `NavigationHandler missing both annotations triggers a finding`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import javax.inject.Inject

            internal class NavigationHandler @Inject constructor(
                private val navigator: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "NavigationHandler with @Inject but no scope annotation must be flagged.",
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
    fun `NavigationModule has no @Inject constructor and is skipped`() {
        // Hilt @Module classes are not constructor-injected. The rule short-circuits at the
        // `hasInject` check and does not require any scope annotation.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.di

            class NavigationModule
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }
}
